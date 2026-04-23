package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.HashEdgeValue
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.code.VersionValue
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.util.getLogger

import java.lang.AutoCloseable
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener

import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.util.retry.Retry

/**
 * Represents a label in the property graph model.
 */
abstract class AbstractLabel<T>(
    override val entity: LabelEntity,
    val coder: EdgeEncoder<T>,
) : Label,
    AutoCloseable {
    val log: Logger = getLogger()

    val isMultiEdge = entity.type == LabelType.MULTI_EDGE

    /**
     * When it was 1 second previously, too many error logs occurred in generally normal situations
     * Only monitor 2 seconds or more.
     */
    private val expireDuration = Duration.ofSeconds(2)

    private val lockMonitor =
        Caffeine
            .newBuilder()
            .expireAfterWrite(expireDuration)
            .maximumSize(1_000_000) // modify to the sufficient size,
            .recordStats()
            .removalListener(
                RemovalListener<String, Long> { traceId, value, cause ->
                    if (cause == RemovalCause.EXPIRED) {
                        val currentTime = System.currentTimeMillis()
                        val delay = currentTime - (value ?: currentTime)
                        log.error(
                            "Lock with traceId {} was not released within {}. The expiration occurred {} ms ago.",
                            traceId,
                            expireDuration,
                            delay,
                        )
                    }
                },
            ).build<String, Long>()

    override fun mutate(
        edges: List<TraceEdge>,
        op: EdgeOperation,
        alias: EntityName?,
        bulk: Boolean,
        failOnExist: Boolean,
        newCollector: () -> StorageOpCollector?,
    ): Mono<List<CdcContext>> {
        if (entity.readOnly) {
            return Mono.error(UnsupportedOperationException("This Label (${entity.fullName}) is read-only"))
        }
        return Flux
            .fromIterable(edges)
            .map { it.ensureType(entity.schema) }
            .flatMapSequential { processEdgeMutation(it, op, alias, bulk, failOnExist, newCollector()) }
            .collectList()
    }

    @Suppress("LongMethod")
    private fun processEdgeMutation(
        edge: TraceEdge,
        op: EdgeOperation,
        alias: EntityName?,
        bulk: Boolean,
        failOnExist: Boolean = false,
        collector: StorageOpCollector? = null,
    ): Mono<CdcContext> {
        log.debug("mutate {} {}", op, edge)
        val encodedHashEdgeKey = coder.encodeHashEdgeKey(edge, entity.id)
        val encodedLockEdge = coder.encodeLockEdge(edge, entity.id)
        // read-modify-write (RMW)
        return acquireLock(edge.traceId, encodedLockEdge, bulk)
            .flatMap {
                log.debug("1. lock acquired: {}", encodedLockEdge)
                log.debug("2. read: {}", encodedHashEdgeKey)
                findHashEdge(encodedHashEdgeKey)
            }.flatMap { existingEncodedHashEdgeValue ->
                log.debug("3. modify & write: {}", existingEncodedHashEdgeValue)
                updateExistingHashEdge(
                    existingEncodedHashEdgeValue,
                    edge,
                    op,
                    encodedHashEdgeKey,
                    failOnExist,
                )
            }.switchIfEmpty {
                log.debug("3. (no existing) create & write: {}", encodedHashEdgeKey)
                createNewHashEdge(edge, op, encodedHashEdgeKey)
            }.flatMap { context ->
                log.debug("4. finalize edge mutation under lock: {}", context.after)
                finalizeEdgeMutationUnderLock(context)
                    .map {
                        context.copy(deferredRequests = context.deferredRequests + it)
                    }
            }.map { context ->
                // apply alias
                if (alias == null || entity.name == alias) {
                    context
                } else {
                    context.copy(alias = alias)
                }
            }.flatMap { context ->
                log.debug("5. update degree")
                // 5. update degree (this is not idempotent)
                updateEdgeDegrees(context)
                    .map { deferredDegrees ->
                        context.copy(deferredRequests = context.deferredRequests + deferredDegrees)
                    }
            }.flatMap { context ->
                if (context.deferredRequests.isNotEmpty()) {
                    log.debug("6. handle {} deferred  requests", context.deferredRequests.size)
                    handleDeferredRequests(context.deferredRequests, collector)
                        .thenReturn(context.copy(deferredRequests = emptyList()))
                } else {
                    Mono.just(context)
                }
            }.map { context -> context.copy(storageOps = collector?.snapshot()) }
            .subscribeOn(Schedulers.boundedElastic())
            .doFinally {
                releaseLock(edge.traceId, encodedLockEdge, bulk)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe()
                log.debug("7. lock released: {}", encodedLockEdge)
            }
    }

    private fun createNewHashEdge(
        edge: TraceEdge,
        op: EdgeOperation,
        encodedKey: EncodedKey<T>,
    ): Mono<CdcContext> {
        val mutationContext = MutationContext.ifNotExists(edge.ts, op)
        val hashEdgeValue =
            HashEdgeValue.from(
                mutationContext.active,
                edge.ts,
                if (op == EdgeOperation.DELETE || op == EdgeOperation.PURGE) emptyMap() else edge.props,
                mutationContext.insertTs,
                mutationContext.deleteTs,
            )

        if (mutationContext.expectedResult == EdgeOperationStatus.PURGED) {
            return Mono.just(
                CdcContext(
                    entity.name,
                    edge,
                    op,
                    mutationContext.expectedResult,
                    null,
                    null,
                    mutationContext.acc,
                ),
            )
        }

        return inplaceFillEmptyToNull(op, mutationContext.active, edge.ts, hashEdgeValue)
            .then(
                Mono.defer {
                    val encodedHashEdgeValue = coder.encodeHashEdgeValue(hashEdgeValue)
                    create(encodedKey, encodedHashEdgeValue).map {
                        CdcContext(
                            entity.name,
                            edge,
                            op,
                            mutationContext.expectedResult,
                            null,
                            HashEdge(
                                hashEdgeValue.active,
                                hashEdgeValue.ts,
                                edge.src,
                                edge.tgt,
                                hashEdgeValue.map.mapValues { it.value.value },
                            ),
                            mutationContext.acc,
                            deferredRequests = it,
                        )
                    }
                },
            )
    }

    @Suppress("LongMethod", "ReturnCount")
    private fun updateExistingHashEdge(
        existingEncodedHashEdgeValue: T,
        edge: TraceEdge,
        op: EdgeOperation,
        encodedKey: EncodedKey<T>,
        failOnExist: Boolean = false,
    ): Mono<CdcContext> {
        val oldHashEdgeValue = coder.decodeHashEdgeValue(existingEncodedHashEdgeValue, entity.schema.hashToFieldNameMap)

        if (failOnExist && oldHashEdgeValue.active.isActive) {
            return Mono.error(IllegalArgumentException("edge already exists"))
        }

        val mutationContext =
            MutationContext.ifExists(
                edge.ts,
                op,
                oldHashEdgeValue.ts,
                oldHashEdgeValue.active,
                oldHashEdgeValue.insertTs,
                oldHashEdgeValue.deleteTs,
            )

        if (mutationContext.expectedResult == EdgeOperationStatus.PURGED) {
            return delete(encodedKey)
                .map {
                    CdcContext(
                        entity.name,
                        edge,
                        op,
                        mutationContext.expectedResult,
                        HashEdge(
                            oldHashEdgeValue.active,
                            oldHashEdgeValue.ts,
                            edge.src,
                            edge.tgt,
                            oldHashEdgeValue.map.mapValues { it.value.value },
                        ),
                        null,
                        mutationContext.acc,
                        deferredRequests = it,
                    )
                }
        }

        val newHashEdgeValue =
            oldHashEdgeValue.copyWith(
                mutationContext.active,
                edge.ts,
                if (op == EdgeOperation.DELETE || op == EdgeOperation.PURGE) emptyMap() else edge.props,
                mutationContext.insertTs,
                mutationContext.deleteTs,
            )

        return inplaceFillEmptyToNull(op, mutationContext.active, edge.ts, newHashEdgeValue)
            .then(
                Mono.defer {
                    val encodedHashEdgeValue = coder.encodeHashEdgeValue(newHashEdgeValue)

                    // IDLE to UPDATED if the edge is actually updated and active
                    // UPDATE to IDLE if the edge is not updated
                    val newMutationContext =
                        if (mutationContext.active == Active.ACTIVE && mutationContext.expectedResult == EdgeOperationStatus.IDLE && oldHashEdgeValue != newHashEdgeValue) {
                            mutationContext.copy(expectedResult = EdgeOperationStatus.UPDATED)
                        } else if (mutationContext.expectedResult == EdgeOperationStatus.UPDATED && oldHashEdgeValue == newHashEdgeValue) {
                            mutationContext.copy(expectedResult = EdgeOperationStatus.IDLE)
                        } else {
                            mutationContext
                        }
                    update(encodedKey, encodedHashEdgeValue)
                        .map {
                            CdcContext(
                                entity.name,
                                edge,
                                op,
                                newMutationContext.expectedResult,
                                HashEdge(
                                    oldHashEdgeValue.active,
                                    oldHashEdgeValue.ts,
                                    edge.src,
                                    edge.tgt,
                                    oldHashEdgeValue.map.mapValues { it.value.value },
                                ),
                                HashEdge(
                                    newHashEdgeValue.active,
                                    newHashEdgeValue.ts,
                                    edge.src,
                                    edge.tgt,
                                    newHashEdgeValue.map.mapValues { it.value.value },
                                ),
                                newMutationContext.acc,
                                deferredRequests = it,
                            )
                        }
                },
            )
    }

    internal fun updateEdgeDegrees(context: CdcContext): Mono<List<Any>> =
        if (context.acc != 0L) {
            when (entity.dirType) {
                DirectionType.BOTH -> {
                    val outboundKey = coder.encodeCounterEdgeKey(context.edge, Direction.OUT, entity.id)
                    val inboundKey = coder.encodeCounterEdgeKey(context.edge, Direction.IN, entity.id)
                    incrby(outboundKey, context.acc)
                        .zipWith(incrby(inboundKey, context.acc))
                        .map {
                            it.t1 + it.t2
                        }
                }
                DirectionType.OUT -> {
                    val outboundKey = coder.encodeCounterEdgeKey(context.edge, Direction.OUT, entity.id)
                    incrby(outboundKey, context.acc)
                }
                DirectionType.IN -> {
                    val inboundKey = coder.encodeCounterEdgeKey(context.edge, Direction.IN, entity.id)
                    incrby(inboundKey, context.acc)
                }
            }
        } else {
            Mono.just(emptyList())
        }

    private fun makeHashEdgeScanKeys(scanFilter: ScanFilter): List<EncodedKey<T>> =
        scanFilter.srcSet.map { srcString ->
            val castedSrc =
                entity.schema.src.dataType
                    .cast(srcString)
            coder.encodeHashEdgeKeyPrefix(castedSrc, entity.id)
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val withEdgeId = withAll || stats.contains(StatKey.EDGE_ID)
        require(scanFilter.dir == Direction.OUT) { "Only OUT direction is supported" }
        val keys = makeHashEdgeScanKeys(scanFilter)
        val rowsWithLastRow =
            Flux
                .fromIterable(keys)
                .flatMap { key ->
                    val marginLimit = scanFilter.limit.coerceAtLeast(scanFilter.limit + 1)
                    scanStorage(key, marginLimit)
                        .map { marginGroup ->
                            // group by key
                            val hasNext = marginGroup.size > scanFilter.limit
                            val group = marginGroup.take(scanFilter.limit)
                            val allRows =
                                group.mapNotNull {
                                    try {
                                        encodedEdgeToSchemaEdge(it)
                                    } catch (e: Throwable) {
                                        log.error("Exception while decoding edge", e.message)
                                        null
                                    }
                                }
                            val rows =
                                allRows
                                    .filter { withAll || it.isActive }
                                    .map {
                                        if (withEdgeId) {
                                            it.toRow(withAll, idEdgeEncoder)
                                        } else {
                                            it.toRow(withAll, null)
                                        }
                                    }
                            val lastValue = group.lastOrNull()
                            val offset = lastValue?.let { coder.encodeOffset(it) }
                            ScanResult(rows, offset, hasNext)
                        }
                }.collectList()
                .defaultIfEmpty(emptyList())

        return rowsWithLastRow.map {
            val rows = it.flatMap { pair -> pair.rows }
            val offsets = it.map { pair -> pair.offset }
            val hasNext = it.map { pair -> pair.hasNext }
            DataFrame(
                rows,
                if (withAll) {
                    entity.schema.allStructType
                } else if (withEdgeId) {
                    entity.schema.edgeIdStructType
                } else {
                    entity.schema.structType
                },
                offsets = offsets,
                hasNext = hasNext,
            )
        }
    }

    override fun close() {
        //
    }

    // --- for mutateSchemaEdges

    internal fun acquireLock(
        traceId: String,
        lockEdge: KeyValue<T>,
        bulk: Boolean = false,
    ): Mono<Boolean> =
        if (bulk) {
            Mono.just(true)
        } else {
            val staleLockClearAttempted = AtomicBoolean(false)

            Mono
                .defer {
                    setnxOnLock(EncodedKey(lockEdge.key), lockEdge.value)
                        .flatMap { isSuccess ->
                            if (isSuccess) {
                                lockMonitor.put(traceId, System.currentTimeMillis())
                                Mono.just(true)
                            } else {
                                // Attempt cleanup if lock is too old (5 minutes) - only once initially
                                if (staleLockClearAttempted.compareAndSet(false, true)) { // Modified part
                                    findStaleLockAndClear(lockEdge as KeyValue<Any>, 300000L)
                                        .onErrorResume { e ->
                                            log.warn("stale sweep failed (ignored): ${e.message}")
                                            Mono.empty()
                                        }
                                        // Trigger retry after cleanup attempt
                                        .then(Mono.error(RuntimeException("Lock acquisition failed, retrying...")))
                                } else {
                                    Mono.error(RuntimeException("Lock acquisition failed, retrying..."))
                                }
                            }
                        }
                }.retryWhen(
                    Retry
                        .backoff(Long.MAX_VALUE, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofMillis(100))
                        .jitter(0.1)
                        .doAfterRetry { retrySignal ->
                            if (retrySignal.totalRetries() >= 49) { // 50 retries = 5 seconds
                                throw Exceptions.propagate(retrySignal.failure())
                            }
                        }.filter { it is RuntimeException }, // Adjust this filter as necessary based on the error you throw
                ).onErrorResume {
                    log.error("Lock acquisition failed...", it)
                    Mono.error(LockAcquisitionFailedException(lockEdge as KeyValue<Any>))
                }
        }

    override fun findStaleLockAndClear(
        lockEdge: KeyValue<Any>,
        lockTimeout: Long,
    ): Mono<Void> {
        var lockEdge = lockEdge as KeyValue<T>
        return findLockValue(EncodedKey(lockEdge.key))
            .flatMap { encodedLockValue ->
                val lockEdgeValue = coder.decodeLockEdgeValue(encodedLockValue)
                if (lockEdgeValue.isStale(lockTimeout)) {
                    log.warn("lock is stale. try to clear the lock.")
                    clearLock(lockEdge, encodedLockValue)
                } else {
                    Mono.empty()
                }
            }
    }

    internal fun clearLock(
        lockEdge: KeyValue<T>,
        value: T,
    ): Mono<Void> = cad(EncodedKey(lockEdge.key), value).then()

    internal fun releaseLock(
        traceId: String,
        lockEdge: KeyValue<T>,
        bulk: Boolean = false,
    ): Mono<Boolean> =
        if (bulk) {
            Mono.just(true)
        } else {
            deleteOnLock(lockEdge)
                .map {
                    if (it) {
                        lockMonitor.invalidate(traceId)
                    }
                    it
                }
        }

    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> {
        val srcAndKeys =
            srcSet.map {
                val (casted, edge) =
                    if (dir == Direction.OUT) {
                        val casted =
                            entity.schema.src.dataType
                                .cast(it) ?: error("Invalid src: $it")
                        casted to Edge(0L, casted, "")
                    } else {
                        val casted =
                            entity.schema.tgt.dataType
                                .cast(it) ?: error("Invalid tgt: $it")
                        casted to Edge(0L, "", casted)
                    }
                casted to coder.encodeCounterEdgeKey(edge, dir, entity.id)
            }

        val rowsMono = getCountRows(srcAndKeys, dir)

        val outputSrcType = if (dir == Direction.OUT) entity.schema.src.dataType else entity.schema.tgt.dataType

        return rowsMono.map { rows ->
            DataFrame(
                rows,
                StructType(
                    arrayOf(
                        Field("src", outputSrcType, false),
                        Field("COUNT(1)", DataType.LONG, false),
                        Field("dir", DataType.STRING, false),
                    ),
                ),
            )
        }
    }

    fun updateHashEdgeValueFields(
        op: EdgeOperation,
        active: Active,
        ts: Long,
        hashEdgeValue: HashEdgeValue,
    ) {
        updateNullableFields(op, ts, hashEdgeValue)
        validateNonNullableFields(active, hashEdgeValue)
    }

    private fun updateNullableFields(
        op: EdgeOperation,
        ts: Long,
        hashEdgeValue: HashEdgeValue,
    ) {
        entity.schema.fields.forEach { field ->
            val versionValue = hashEdgeValue.map[field.name]
            val isNewInsert = hashEdgeValue.deleteTs == null || ts >= hashEdgeValue.deleteTs
            val isNewDelete = hashEdgeValue.insertTs == null || ts >= hashEdgeValue.insertTs
            when {
                op == EdgeOperation.INSERT && field.isNullable && versionValue == null && isNewInsert -> {
                    hashEdgeValue.map[field.name] = VersionValue(ts, null)
                }

                op == EdgeOperation.DELETE && (versionValue == null || versionValue.version <= ts) && isNewDelete -> {
                    hashEdgeValue.map[field.name] = VersionValue(ts, null)
                }
            }
        }
    }

    private fun validateNonNullableFields(
        active: Active,
        hashEdgeValue: HashEdgeValue,
    ) {
        if (active != Active.ACTIVE) return

        val nullableFieldErrors =
            entity.schema.fields
                .filter { !it.isNullable && hashEdgeValue.map[it.name]?.value == null }
                .map { "Field is not null : ${it.name}" }

        require(nullableFieldErrors.isEmpty()) {
            "Validation error(s): ${nullableFieldErrors.joinToString()}"
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun inplaceFillEmptyToNull(
        op: EdgeOperation,
        active: Active,
        ts: Long,
        hashEdgeValue: HashEdgeValue,
    ): Mono<Void> {
        // This will be replaced with the following code
        // ```
        // updateNullableFields(op, ts, hashEdgeValue)
        // return try {
        //     validateNonNullableFields(active, hashEdgeValue)
        //     Mono.empty()
        // } catch (e: IllegalArgumentException) {
        //     log.error("Validation error(s): ${e.message}")
        //     Mono.error(e)
        // }
        // ```
        entity.schema.fields.forEach { field ->
            val versionValue = hashEdgeValue.map[field.name]
            val isNewInsert = hashEdgeValue.deleteTs == null || ts >= hashEdgeValue.deleteTs
            val isNewDelete = hashEdgeValue.insertTs == null || ts >= hashEdgeValue.insertTs
            when {
                op == EdgeOperation.INSERT && field.isNullable && versionValue == null && isNewInsert -> {
                    hashEdgeValue.map[field.name] = VersionValue(ts, null)
                }

                op == EdgeOperation.DELETE && (versionValue == null || versionValue.version <= ts) && isNewDelete -> {
                    hashEdgeValue.map[field.name] = VersionValue(ts, null)
                }
            }
        }
        val errors =
            if (active == Active.ACTIVE) {
                entity.schema.fields
                    .filter { field -> !field.isNullable && hashEdgeValue.map[field.name]?.value == null }
                    .joinToString { "Field is not null : ${it.name}" }
            } else {
                ""
            }

        return if (errors.isNotEmpty()) {
            val message = "Validation error(s): $errors"
            log.error(message)
            Mono.error(IllegalArgumentException(message))
        } else {
            Mono.empty()
        }
    }

    abstract fun getCountRows(
        srcAndKeys: List<Pair<Any, T>>,
        dir: Direction,
    ): Mono<List<Row>>

    open fun finalizeEdgeMutationUnderLock(context: CdcContext): Mono<List<Any>> = Mono.just(emptyList())

    abstract fun findHashEdge(keyField: EncodedKey<T>): Mono<T>

    abstract fun create(
        keyField: EncodedKey<T>,
        value: T,
    ): Mono<List<Any>>

    abstract fun update(
        keyField: EncodedKey<T>,
        value: T,
    ): Mono<List<Any>>

    abstract fun delete(keyField: EncodedKey<T>): Mono<List<Any>>

    abstract fun deleteOnLock(keyField: KeyValue<T>): Mono<Boolean>

    open fun handleDeferredRequests(
        deferredRequests: List<Any>,
        storageOpCollector: StorageOpCollector? = null,
    ): Mono<Boolean> = Mono.just(true)

    abstract fun setnx(
        keyField: EncodedKey<T>,
        value: T,
    ): Mono<Boolean>

    open fun setnxOnLock(
        keyField: EncodedKey<T>,
        value: T,
    ): Mono<Boolean> = setnx(keyField, value)

    abstract fun cad(
        keyField: EncodedKey<T>,
        value: T,
    ): Mono<Long>

    abstract fun findLockValue(keyField: EncodedKey<T>): Mono<T>

    // --- counter
    abstract fun incrby(
        key: T,
        acc: Long,
    ): Mono<List<Any>>

    // --- for scan

    abstract fun scanStorage(
        prefix: EncodedKey<T>,
        limit: Int,
        start: EncodedKey<T>? = null,
        end: EncodedKey<T>? = null,
    ): Mono<List<KeyFieldValue<T>>>

    abstract fun encodedEdgeToSchemaEdge(keyFieldValue: KeyFieldValue<T>): SchemaEdge

    override fun toString(): String = "Label(entity=$entity)"
}

class LockAcquisitionFailedException(
    val lockEdge: KeyValue<Any>,
) : RuntimeException(
        "Lock acquisition failed, retrying...",
    )

enum class Measure(
    val field: String,
) {
    COUNT("count"),
    SUM("sum"),
    AVG("avg"),
    MAX("max"),
    MIN("min"),
    ;

    companion object {
        fun of(field: String): Measure = values().first { it.field == field }
    }
}
