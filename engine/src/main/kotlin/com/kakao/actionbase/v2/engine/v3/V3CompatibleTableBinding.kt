package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.mutation.EdgeMutationRecords
import com.kakao.actionbase.core.edge.payload.EdgeMutationStatus
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationStatus
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.core.state.transit
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel

import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

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
        return mutate(
            source = source,
            target = target,
            events = events,
            acquireLock = acquireLock,
            mapper = mapper,
            codeToFieldNameMap = codeToFieldNameMap,
            buildMutationRecords = { beforeRecord, afterRecord ->
                EdgeMutationBuilder.buildForUniqueEdge(beforeRecord, afterRecord, schema.direction, schema.indexes, schema.groups)
            },
            toStatus = { count, status, before, after, acc ->
                EdgeMutationStatus(source, target, count, status, before, after, acc)
            },
        )
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
        return mutate(
            source = key,
            target = key,
            events = events,
            acquireLock = acquireLock,
            mapper = mapper,
            codeToFieldNameMap = codeToFieldNameMap,
            buildMutationRecords = { beforeRecord, afterRecord ->
                EdgeMutationBuilder.buildForMultiEdge(beforeRecord, afterRecord, schema.direction, schema.indexes, schema.groups)
            },
            toStatus = { count, status, before, after, acc ->
                MultiEdgeMutationStatus(key, count, status, before, after, acc)
            },
        )
    }

    private fun <S : Any> mutate(
        source: Any,
        target: Any,
        events: List<Event>,
        acquireLock: Boolean,
        mapper: EdgeRecordMapper,
        codeToFieldNameMap: Map<Int, String>,
        buildMutationRecords: (EdgeStateRecord, EdgeStateRecord) -> EdgeMutationRecords,
        toStatus: (Int, String, State, State, Long) -> S,
    ): Mono<S> {
        with(label) {
            val eventId = events.first().id
            val compatibleEdge = Edge(0L, source, target)
            return withLock(eventId, compatibleEdge, !acquireLock) {
                // v2
                findHashEdge(coder.encodeHashEdgeKey(compatibleEdge, entity.id))
                    .map {
                        // v2 -> v3
                        decodeV2HashEdgeToState(it, mapper, codeToFieldNameMap)
                    }.switchIfEmpty(Mono.defer { Mono.just(State.initial) })
                    .map { before ->
                        // v3
                        val after =
                            events.fold(before) { acc, event ->
                                acc.transit(event, descriptor.schema)
                            }
                        before.specialStateValueToNull() to after.specialStateValueToNull()
                    }.flatMap { (before, after) ->
                        val beforeRecord = EdgeStateRecord.of(source, target, before, entity.id)
                        val afterRecord = EdgeStateRecord.of(source, target, after, entity.id)
                        val mutationRecords = buildMutationRecords(beforeRecord, afterRecord)
                        handleDeferredRequests(buildHBaseMutations(mutationRecords, mapper))
                            .thenReturn(
                                toStatus(events.size, mutationRecords.status, before, after, mutationRecords.acc),
                            )
                    }.subscribeOn(Schedulers.boundedElastic())
            }
        }
    }

    private fun <S> HBaseIndexedLabel.withLock(
        traceId: String,
        edge: Edge,
        bulk: Boolean,
        action: () -> Mono<S>,
    ): Mono<S> {
        val lockEdge = coder.encodeLockEdge(edge, entity.id)
        return acquireLock(traceId, lockEdge, bulk)
            .flatMap { action() }
            .doFinally {
                releaseLock(traceId, lockEdge, bulk)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe()
            }
    }

    private fun decodeV2HashEdgeToState(
        encodedValue: ByteArray,
        mapper: EdgeRecordMapper,
        codeToFieldNameMap: Map<Int, String>,
    ): State {
        val stateValue = mapper.state.decoder.decodeValue(encodedValue)
        return State(
            stateValue.active,
            stateValue.version,
            stateValue.createdAt,
            stateValue.deletedAt,
            stateValue.properties
                .mapNotNull { (key, value) ->
                    codeToFieldNameMap[key]?.let { name -> name to value }
                }.toMap(),
        )
    }

    private fun buildHBaseMutations(
        mutationRecords: EdgeMutationRecords,
        mapper: EdgeRecordMapper,
    ): List<Mutation> {
        val mutations = mutableListOf<Mutation>()
        val record = mapper.state.encoder.encode(mutationRecords.stateRecord)
        mutations +=
            Put(record.key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, record.value)
        mutations +=
            mutationRecords.createIndexRecords.map {
                val record = mapper.index.encoder.encode(it)
                Put(record.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, record.value)
            }
        mutations +=
            mutationRecords.countRecords.map {
                val key = mapper.count.encoder.encodeKey(it.key)
                Increment(key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, it.value)
            }
        mutations +=
            mutationRecords.deleteIndexRecordKeys.map {
                val key = mapper.index.encoder.encodeKey(it)
                Delete(key)
            }
        mutations +=
            mutationRecords.groupRecords.groupBy { it.key to it.ttl }.map { (groupKey, records) ->
                val (key, ttl) = groupKey
                val encodedKey = mapper.group.encoder.encodeKey(key)
                val increment = Increment(encodedKey)
                records.mergeQualifiers().forEach { (mergedQualifier, mergedValue) ->
                    val qualifier = mapper.group.encoder.encodeQualifier(mergedQualifier)
                    increment.addColumn(Constants.DEFAULT_COLUMN_FAMILY, qualifier, mergedValue)
                }
                if (ttl != null && ttl != Long.MAX_VALUE && ttl > 0) {
                    increment.ttl = ttl
                }
                increment
            }
        return mutations
    }

    companion object {
        internal fun State.specialStateValueToNull(): State =
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
