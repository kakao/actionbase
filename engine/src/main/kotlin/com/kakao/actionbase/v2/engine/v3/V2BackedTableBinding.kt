package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.mutation.EdgeMutationRecords
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.binding.MutationRecordsSummary
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.engine.label.LockAcquisitionFailedException
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel

import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class V2BackedTableBinding(
    private val descriptor: V3TableDescriptor,
    private val label: HBaseIndexedLabel,
    private val mapper: EdgeRecordMapper,
    private val lockTimeout: Long,
) : TableBinding {
    override val table: String = descriptor.table
    override val schema: ModelSchema = descriptor.schema
    override val mutationMode: MutationMode = MutationMode.valueOf(label.entity.mode.name)

    override fun <T> withLock(
        key: MutationKey,
        action: () -> Mono<T>,
    ): Mono<T> {
        val (source, target) = key.toSourceTarget()
        val traceId = "$key@${System.currentTimeMillis()}"
        with(label) {
            val compatibleEdge = Edge(0L, source, target)
            val lockEdge = coder.encodeLockEdge(compatibleEdge, entity.id)
            return acquireLock(traceId, lockEdge, false)
                .flatMap { action() }
                .doFinally {
                    releaseLock(traceId, lockEdge, false)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe({}, { log.error("Lock release failed for {}", key, it) })
                }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    override fun read(key: MutationKey): Mono<State> {
        val (source, target) = key.toSourceTarget()
        with(label) {
            val compatibleEdge = Edge(0L, source, target)
            return findHashEdge(coder.encodeHashEdgeKey(compatibleEdge, entity.id))
                .map { decodeV2HashEdgeToState(it) }
                .switchIfEmpty(Mono.defer { Mono.just(State.initial) })
                .subscribeOn(Schedulers.boundedElastic())
        }
    }

    override fun write(
        key: MutationKey,
        before: State,
        after: State,
    ): Mono<MutationRecordsSummary> {
        val (source, target) = key.toSourceTarget()
        val beforeClean = before.specialStateValueToNull()
        val afterClean = after.specialStateValueToNull()
        val beforeRecord = EdgeStateRecord.of(source, target, beforeClean, label.entity.id)
        val afterRecord = EdgeStateRecord.of(source, target, afterClean, label.entity.id)
        val records = buildMutationRecords(beforeRecord, afterRecord)
        return label
            .handleDeferredRequests(buildHBaseMutations(records))
            .thenReturn(MutationRecordsSummary(records.status, records.acc, beforeClean, afterClean))
    }

    override fun handleMutationError(error: Throwable) {
        if (error is LockAcquisitionFailedException) {
            label
                .findStaleLockAndClear(error.lockEdge, lockTimeout)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe({}, { log.error("Stale lock clear failed", it) })
        }
    }

    private fun buildMutationRecords(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
    ): EdgeMutationRecords {
        val schema = descriptor.schema
        return when (schema) {
            is ModelSchema.Edge ->
                EdgeMutationBuilder.buildForUniqueEdge(before, after, schema.direction, schema.indexes, schema.groups, schema.caches)
            is ModelSchema.MultiEdge ->
                EdgeMutationBuilder.buildForMultiEdge(before, after, schema.direction, schema.indexes, schema.groups, schema.caches)
        }
    }

    private fun decodeV2HashEdgeToState(encodedValue: ByteArray): State {
        val codeToFieldNameMap = schema.codeToName
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

    private fun buildHBaseMutations(mutationRecords: EdgeMutationRecords): List<Mutation> {
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
        mutations +=
            mutationRecords.createCacheRecords.map {
                val encoded = mapper.cache.encoder.encode(it)
                Put(encoded.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, encoded.qualifier, encoded.value)
            }
        mutations +=
            mutationRecords.deleteCacheRecordQualifiers.map { (key, qualifier) ->
                val encodedKey = mapper.cache.encoder.encodeKey(key)
                val encodedQualifier = mapper.cache.encoder.encodeQualifier(qualifier)
                Delete(encodedKey)
                    .addColumns(Constants.DEFAULT_COLUMN_FAMILY, encodedQualifier)
            }
        return mutations
    }

    companion object {
        private val log = LoggerFactory.getLogger(V2BackedTableBinding::class.java)

        private fun MutationKey.toSourceTarget(): Pair<Any, Any> =
            when (this) {
                is MutationKey.SourceTarget -> source to target
                is MutationKey.Id -> id to id
            }

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
