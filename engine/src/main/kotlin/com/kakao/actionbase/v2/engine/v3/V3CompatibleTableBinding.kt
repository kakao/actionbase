package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.engine.storage.Delete as StorageDelete
import com.kakao.actionbase.v2.engine.storage.Increment as StorageIncrement
import com.kakao.actionbase.v2.engine.storage.Put as StoragePut

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.payload.EdgeMutationStatus
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationStatus
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.core.state.transit
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.storage.StorageOperation

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

// ... other imports ...

class V3CompatibleTableBinding(
    private val descriptor: V3TableDescriptor,
    private val label: HBaseIndexedLabel,
) {
    val schema: ModelSchema
        get() = descriptor.schema

    fun mutateEdge(
        key: Pair<Any, Any>,
        events: List<Event>,
        acquireLock: Boolean,
        mapper: EdgeRecordMapper,
        codeToFieldNameMap: Map<Int, String>,
    ): Mono<EdgeMutationStatus> {
        val schema = descriptor.schema
        require(schema is ModelSchema.Edge) {
            "mutateEdge is only supported for Edge schema, but got ${schema::class.java.simpleName}"
        }

        val (source, target) = key
        with(label) {
            val eventId = events.first().id
            val compatibleEdge = Edge(0L, source, target)
            val encodedHashEdgeKey = coder.encodeHashEdgeKey(compatibleEdge, entity.id)
            val encodedLockEdge = coder.encodeLockEdge(compatibleEdge, entity.id)
            val bulk = !acquireLock
            return acquireLock(eventId, encodedLockEdge, bulk)
                .flatMap {
                    findHashEdge(encodedHashEdgeKey)
                }.map {
                    // extract the state from the encoded value
                    val stateValue = mapper.state.decoder.decodeValue(it)
                    State(
                        stateValue.active,
                        stateValue.version,
                        stateValue.createdAt,
                        stateValue.deletedAt,
                        stateValue.properties
                            .mapNotNull { (key, value) ->
                                codeToFieldNameMap[key]?.let { name -> name to value }
                            }.toMap(),
                    )
                }.switchIfEmpty(Mono.defer { Mono.just(State.initial) })
                .map { before ->
                    val after =
                        events.fold(before) { acc, event ->
                            acc.transit(event, schema)
                        }
                    before.specialStateValueToNull() to after.specialStateValueToNull()
                }.flatMap { (before, after) ->
                    val beforeRecord = EdgeStateRecord.of(source, target, before, entity.id)
                    val afterRecord = EdgeStateRecord.of(source, target, after, entity.id)
                    val mutationRecords = EdgeMutationBuilder.build(beforeRecord, afterRecord, schema.direction, schema.indexes, schema.groups)
                    val mutations = mutableListOf<StorageOperation>()
                    val record = mapper.state.encoder.encode(mutationRecords.stateRecord)
                    mutations +=
                        StorageOperation.PutOp(StoragePut(record.key, record.value))
                    mutations +=
                        mutationRecords.createIndexRecords.map {
                            val record = mapper.index.encoder.encode(it)
                            StorageOperation.PutOp(StoragePut(record.key, record.value))
                        }
                    mutations +=
                        mutationRecords.countRecords.map {
                            val key = mapper.count.encoder.encodeKey(it.key)
                            StorageOperation.IncrementOp(StorageIncrement(key, it.value))
                        }
                    mutations +=
                        mutationRecords.deleteIndexRecordKeys.map {
                            val key = mapper.index.encoder.encodeKey(it)
                            StorageOperation.DeleteOp(StorageDelete(key))
                        }
                    mutations +=
                        mutationRecords.groupRecords.groupBy { it.key to it.ttl }.flatMap { (groupKey, records) ->
                            val (key, ttl) = groupKey
                            val encodedKey = mapper.group.encoder.encodeKey(key)
                            records.mergeQualifiers().map { (mergedQualifier, mergedValue) ->
                                StorageOperation.IncrementOp(StorageIncrement(encodedKey, mergedValue))
                            }
                        }
                    handleDeferredRequests(mutations)
                        .thenReturn(
                            EdgeMutationStatus(source, target, events.size, mutationRecords.status, before, after, mutationRecords.acc),
                        )
                }.subscribeOn(Schedulers.boundedElastic())
                .doFinally {
                    releaseLock(eventId, encodedLockEdge, bulk)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe()
                }
        }
    }

    fun mutateMultiEdge(
        key: Any,
        events: List<Event>,
        acquireLock: Boolean,
        mapper: EdgeRecordMapper,
        codeToFieldNameMap: Map<Int, String>,
    ): Mono<MultiEdgeMutationStatus> {
        val schema = descriptor.schema
        require(schema is ModelSchema.MultiEdge) {
            "mutateMultiEdge is only supported for MultiEdge schema, but got ${schema::class.java.simpleName}"
        }

        with(label) {
            val eventId = events.first().id
            val compatibleEdge = Edge(0L, key, key)
            val encodedHashEdgeKey = coder.encodeHashEdgeKey(compatibleEdge, entity.id)
            val encodedLockEdge = coder.encodeLockEdge(compatibleEdge, entity.id)
            val bulk = !acquireLock
            return acquireLock(eventId, encodedLockEdge, bulk)
                .flatMap {
                    findHashEdge(encodedHashEdgeKey)
                }.map {
                    // extract the state from the encoded value
                    val stateValue = mapper.state.decoder.decodeValue(it)
                    State(
                        stateValue.active,
                        stateValue.version,
                        stateValue.createdAt,
                        stateValue.deletedAt,
                        stateValue.properties
                            .mapNotNull { (key, value) ->
                                codeToFieldNameMap[key]?.let { name -> name to value }
                            }.toMap(),
                    )
                }.switchIfEmpty(Mono.defer { Mono.just(State.initial) })
                .map { before ->
                    val after =
                        events.fold(before) { acc, event ->
                            acc.transit(event, schema)
                        }
                    before.specialStateValueToNull() to after.specialStateValueToNull()
                }.flatMap { (before, after) ->
                    val beforeRecord = EdgeStateRecord.of(key, key, before, entity.id)
                    val afterRecord = EdgeStateRecord.of(key, key, after, entity.id)
                    val mutationRecords = EdgeMutationBuilder.buildForMultiEdge(beforeRecord, afterRecord, schema.direction, schema.indexes, schema.groups)
                    val mutations = mutableListOf<StorageOperation>()
                    val record = mapper.state.encoder.encode(mutationRecords.stateRecord)
                    mutations +=
                        StorageOperation.PutOp(StoragePut(record.key, record.value))
                    mutations +=
                        mutationRecords.createIndexRecords.map {
                            val record = mapper.index.encoder.encode(it)
                            StorageOperation.PutOp(StoragePut(record.key, record.value))
                        }
                    mutations +=
                        mutationRecords.countRecords.map {
                            val key = mapper.count.encoder.encodeKey(it.key)
                            StorageOperation.IncrementOp(StorageIncrement(key, it.value))
                        }
                    mutations +=
                        mutationRecords.deleteIndexRecordKeys.map {
                            val key = mapper.index.encoder.encodeKey(it)
                            StorageOperation.DeleteOp(StorageDelete(key))
                        }
                    mutations +=
                        mutationRecords.groupRecords.groupBy { it.key to it.ttl }.flatMap { (groupKey, records) ->
                            val (key, ttl) = groupKey
                            val encodedKey = mapper.group.encoder.encodeKey(key)
                            records.mergeQualifiers().map { (mergedQualifier, mergedValue) ->
                                StorageOperation.IncrementOp(StorageIncrement(encodedKey, mergedValue))
                            }
                        }
                    handleDeferredRequests(mutations)
                        .thenReturn(
                            MultiEdgeMutationStatus(key, events.size, mutationRecords.status, before, after, mutationRecords.acc),
                        )
                }.subscribeOn(Schedulers.boundedElastic())
                .doFinally {
                    releaseLock(eventId, encodedLockEdge, bulk)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe()
                }
        }
    }

    companion object {
        private fun State.specialStateValueToNull(): State =
            State(
                active = this.active,
                version = this.version,
                createdAt = this.createdAt,
                deletedAt = this.deletedAt,
                properties =
                    this.properties.mapValues { (_, value) ->
                        if (SpecialStateValue.isSpecialStateValue(value.value)) {
                            value.copy(value = null)
                        } else {
                            value
                        }
                    },
            )

        fun List<EdgeGroupRecord>.mergeQualifiers(): Map<EdgeGroupRecord.Qualifier, Long> =
            this
                .groupingBy { it.qualifier }
                .fold(0L) { acc, record -> acc + record.value }
                .filterValues { it != 0L }
    }
}
