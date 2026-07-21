package com.kakao.actionbase.v2.engine.label.mixin

import com.kakao.actionbase.v2.core.code.EdgeBuffer
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.edge.toRowWithOffset
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.ScanResult
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.WherePredicate

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexedLabelMixin<T> {
    val self: AbstractLabel<T>

    val indices: List<Index>

    val indexNameToIndex: Map<String, Index>

    @Suppress("LongMethod")
    fun createStartStop(
        scanFilter: ScanFilter,
        index: Index,
    ): List<StartStop> {
        val wherePredicates = scanFilter.otherPredicates.associateBy { it.key }

        return index.fields
            .takeWhile { wherePredicates.contains(it.name) }
            .map {
                when (val z = wherePredicates[it.name]) {
                    is WherePredicate.Eq -> {
                        val item = StartStopItem(it, true, z.value)
                        StartStop(item, item)
                    }
                    is WherePredicate.Lt -> {
                        val item = StartStopItem(it, false, z.value)
                        if (it.order == Order.DESC) {
                            StartStop(item, null)
                        } else {
                            StartStop(null, item)
                        }
                    }
                    is WherePredicate.Lte -> {
                        val item = StartStopItem(it, true, z.value)
                        if (it.order == Order.DESC) {
                            StartStop(item, null)
                        } else {
                            StartStop(null, item)
                        }
                    }
                    is WherePredicate.Gt -> {
                        val item = StartStopItem(it, false, z.value)
                        if (it.order == Order.ASC) {
                            StartStop(item, null)
                        } else {
                            StartStop(null, item)
                        }
                    }
                    is WherePredicate.Gte -> {
                        val item = StartStopItem(it, true, z.value)
                        if (it.order == Order.ASC) {
                            StartStop(item, null)
                        } else {
                            StartStop(null, item)
                        }
                    }
                    is WherePredicate.Between -> {
                        val start = StartStopItem(it, true, z.from)
                        val stop = StartStopItem(it, true, z.to)
                        if (it.order == Order.ASC) {
                            StartStop(start, stop)
                        } else {
                            StartStop(stop, start)
                        }
                    }
                    else -> {
                        error("Unsupported predicate type: $z")
                    }
                }
            }.map { startStop ->
                startStop.ensureType(self.entity.schema.allStructType)
            }
    }

    /**
     * NOTE: start key is inclusive.
     *
     * desc
     *          start                               stop
     *          --------------------------------------->
     * value      15     14     13     12     11     10
     * encoded   0x0    0x1    0x2    0x3    0x4    0x5
     *                                 ^      ^
     *                                 |      |
     *                                 |      |
     * lte:12 -- start key(0x3) -------|      |
     * lt:12 -- 0x3 -- +1 --- start key(0x4)--|
     *
     * asc
     *          start                               stop
     *          --------------------------------------->
     * value      10     11     12     13     14     15
     * encoded   0xA    0xB    0xC    0xD    0xE    0xF
     *                                 ^      ^
     *                                 |      |
     *                                 |      |
     * gte:13 -- start key(0xD) -------|      |
     * gt:13 -- 0xD -- +1 --- start key(0xE)--|
     */
    fun encodeIndexedFieldsStartItemsToBuffer(
        buffer: EdgeBuffer,
        startStops: List<StartStop>,
    ) {
        startStops
            .forEach { (start, _) ->
                if (start != null) {
                    buffer.encodeAny(start.value, start.field.order)
                    if (!start.inclusive) {
                        buffer.plusOne()
                    }
                }
            }
    }

    /**
     * NOTE: stop key is exclusive.
     *
     * desc
     *          start                               stop
     *          --------------------------------------->
     * value      15     14     13     12     11     10
     * encoded   0x0    0x1    0x2    0x3    0x4    0x5
     *                                 ^      ^
     *                                 |      |
     *                                 |      |
     * gt:12 -- 0x3 -- stop key(0x3) --|      |
     * gte:12 -- 0x3 -- +1 --- stop key(0x4)--|
     *
     * asc
     *          start                               stop
     *          --------------------------------------->
     * value      10     11     12     13     14     15
     * encoded   0xA    0xB    0xC    0xD    0xE    0xF
     *                                 ^      ^
     *                                 |      |
     *                                 |      |
     * lt:13 -- 0xD -- stop key(0xD) --|      |
     * lte:13 -- 0xD -- +1 --- stop key(0xE)--|
     */
    fun encodeIndexedFieldsStopItemsToBuffer(
        buffer: EdgeBuffer,
        startStops: List<StartStop>,
    ) {
        val lastIndex = startStops.indexOfLast { it.stop != null }

        startStops.forEachIndexed { index, (_, stop) ->
            if (stop != null) {
                buffer.encodeAny(stop.value, stop.field.order)
                if (stop.inclusive && index == lastIndex) {
                    // only apply +1 to the last stop component
                    buffer.plusOne()
                }
            }
        }
    }

    fun encodeIndexedEdgeKeys(edge: HashEdge): Mono<List<EncodedKey<T>>> =
        encodeIndexedEdges(edge)
            .map { indexedEdges ->
                indexedEdges.map { indexedEdge ->
                    EncodedKey(indexedEdge.key, indexedEdge.field)
                }
            }

    fun encodeIndexedEdges(edge: HashEdge): Mono<List<KeyFieldValue<T>>> =
        Mono
            .fromCallable {
                self.coder.encodeAllIndexedEdges(
                    edge.ts,
                    edge.src,
                    edge.tgt,
                    edge.props,
                    self.entity.dirType,
                    self.entity.id,
                    indices,
                )
            }

    fun makeIndexedEdgeScanKeys(scanFilter: ScanFilter): List<RangeKey<T>> {
        val index = indexNameToIndex[scanFilter.indexName] ?: error("Index not found: ${scanFilter.indexName}")
        val startStops = createStartStop(scanFilter, index)
        return scanFilter.srcSet.map { srcString ->
            val castedSrc =
                if (scanFilter.dir == Direction.OUT) {
                    self.entity.schema.src.dataType
                        .castNotNull(srcString)
                } else {
                    self.entity.schema.tgt.dataType
                        .castNotNull(srcString)
                }

            val encodedKey =
                self.coder.encodeIndexedEdgeKeyPrefix(
                    castedSrc,
                    scanFilter.dir,
                    self.entity.id,
                    index,
                ) {}

            val offset = scanFilter.offset

            val start =
                if (offset != null) {
                    self.coder.encodeIndexedEdgeKeyPrefix(
                        castedSrc,
                        scanFilter.dir,
                        self.entity.id,
                        index,
                    ) { buffer ->
                        val decoded = self.coder.decodeOffset(offset)
                        buffer.put(decoded, 0, decoded.size)
                    }
                } else {
                    self.coder.encodeIndexedEdgeKeyPrefix(
                        castedSrc,
                        scanFilter.dir,
                        self.entity.id,
                        index,
                    ) { buffer ->
                        encodeIndexedFieldsStartItemsToBuffer(buffer, startStops)
                    }
                }

            val isStop = startStops.mapNotNull { it.stop }.any()

            val stop =
                if (isStop) {
                    self.coder.encodeIndexedEdgeKeyPrefix(
                        castedSrc,
                        scanFilter.dir,
                        self.entity.id,
                        index,
                    ) { buffer ->
                        encodeIndexedFieldsStopItemsToBuffer(buffer, startStops)
                    }
                } else {
                    null
                }

            RangeKey(castedSrc, encodedKey, start, stop)
        }
    }

    fun deleteIndexedEdges(keys: List<EncodedKey<T>>): Mono<List<Any>> =
        Flux
            .fromIterable(keys)
            .flatMap {
                self.delete(it)
            }.collectList()
            .map {
                it.flatten()
            }

    /**
     * Scans one page of the given range and deletes the matched index rows, returning the count.
     * Correct only for immutable edges, whose sole persisted records are these index rows, so
     * deleting the scanned keys is a complete delete (no State row or count to reconcile). Bounded by
     * `scanFilter.limit`; callers loop to trim a larger range.
     */
    fun scanAndDeleteIndexedEdges(scanFilter: ScanFilter): Mono<Int> =
        Flux
            .fromIterable(makeIndexedEdgeScanKeys(scanFilter))
            .flatMap { rangeKey ->
                self.scanStorage(rangeKey.prefix, scanFilter.limit, rangeKey.start, rangeKey.stop)
            }.flatMap { group ->
                if (group.isEmpty()) {
                    Mono.just(0)
                } else {
                    deleteIndexedEdges(group.map { EncodedKey(it.key) })
                        .flatMap { deferred -> self.handleDeferredRequests(deferred, null).thenReturn(group.size) }
                }
            }.reduce(0) { acc, deleted -> acc + deleted }

    fun insertIndexedEdges(keyFieldValues: List<KeyFieldValue<T>>): Mono<List<Any>> =
        Flux
            .fromIterable(keyFieldValues)
            .flatMap {
                self.create(EncodedKey(it.key, it.field), it.value)
            }.collectList()
            .map {
                it.flatten()
            }

    /**
     * Comparison between the legacy and the new version v2:
     *
     * Legacy(mutateIndexedEdges)                        | V2(mutateIndexedEdgesV2)
     * --------------------------------------------------|---------------------------------
     * status  | before   | op     | after    | result   | before  | after    | result
     * --------|----------|--------|----------|----------|---------|----------|------------
     * IDLE    | -        | -      | -        | -        | x       | y (x==y) | -
     * -       | not null | DELETE | -        | delete   | ACTIVE  | !ACTIVE  | delete
     * -       | not null | -      | null     | -        | -       | -        | -
     * -       | not null | -      | not null | update   | ACTIVE  | ACTIVE   | update
     * -       | null     | INSERT | null     | -        | -       | -        | -
     * -       | null     | INSERT | not null | insert   | !ACTIVE | ACTIVE   | insert
     * -       | -        | -      | -        | -        | else    | else     | -
     */
    @Suppress("NestedBlockDepth")
    fun mutateIndexedEdges(context: CdcContext): Mono<List<Any>> =
        with(context) {
            when {
                status == EdgeOperationStatus.IDLE -> {
                    Mono.just(emptyList())
                }
                before != null -> {
                    when (op) {
                        EdgeOperation.DELETE, EdgeOperation.PURGE -> {
                            performDeleteEdges(before)
                        }
                        else -> {
                            if (after == null) {
                                Mono.just(emptyList())
                            } else {
                                performUpdateEdges(before, after)
                            }
                        }
                    }
                }
                op == EdgeOperation.INSERT -> {
                    if (after == null) {
                        Mono.just(emptyList())
                    } else {
                        performInsertEdges(after)
                    }
                }
                else -> {
                    Mono.just(emptyList())
                }
            }
        }

    // Performs the deletion logic given the previous (before) state.
    private fun performDeleteEdges(before: HashEdge): Mono<List<Any>> =
        encodeIndexedEdgeKeys(before)
            .flatMap { deleteIndexedEdges(it) }

    // Performs the insertion logic given the new (after) state.
    private fun performInsertEdges(after: HashEdge): Mono<List<Any>> =
        encodeIndexedEdges(after)
            .flatMap { insertIndexedEdges(it) }

    // Performs the update logic, which first computes which keys should be deleted and then updates.
    private fun performUpdateEdges(
        before: HashEdge,
        after: HashEdge,
    ): Mono<List<Any>> {
        val oldIndexedEdgeKeys: Mono<List<EncodedKey<T>>> = encodeIndexedEdgeKeys(before)
        val newIndexedEdges = encodeIndexedEdges(after)

        val zipped =
            newIndexedEdges.zipWith(oldIndexedEdgeKeys) { newKeyFieldValue, oldEncodedKey ->
                newKeyFieldValue to oldEncodedKey
            }

        return zipped.flatMap { (newKeyFieldValue, oldEncodedKey) ->
            Mono
                .fromCallable {
                    // The keys in newIndexedEdges that will be updated
                    val newEncodedKeys = newKeyFieldValue.map { EncodedKey(it.key, it.field) }
                    val willBeUpdated: Set<EncodedKey<T>> = newEncodedKeys.toSet()
                    // From the old keys, only delete those that are not going to be updated
                    oldEncodedKey.filter { !willBeUpdated.contains(it) }
                }.flatMap { deleteIndexedEdges(it) }
                .flatMap { deferredDeletions ->
                    insertIndexedEdges(newKeyFieldValue)
                        .map { deferredInsertions -> deferredDeletions + deferredInsertions }
                }
        }
    }

    /**
     * | before  | after    | result  |
     * |---------|----------|---------|
     * | x       | y (x==y) | -       |
     * | !ACTIVE | ACTIVE   | insert  |
     * | ACTIVE  | !ACTIVE  | delete  |
     * | ACTIVE  | ACTIVE   | update  |
     * | else    | else     | -       |
     */
    fun mutateIndexedEdgesV2(cdcContext: CdcContext): Mono<List<Any>> {
        val before = cdcContext.before
        val after = cdcContext.after

        val beforeActive = before?.active == Active.ACTIVE
        val afterActive = after?.active == Active.ACTIVE

        return when {
            before == after -> {
                Mono.just(emptyList())
            }
            !beforeActive && afterActive -> {
                requireNotNull(after)
                performInsertEdges(after)
            }
            beforeActive && !afterActive -> {
                requireNotNull(before)
                performDeleteEdges(before)
            }
            beforeActive && afterActive -> {
                requireNotNull(before)
                requireNotNull(after)
                performUpdateEdges(before, after)
            }
            else -> {
                Mono.just(emptyList())
            }
        }
    }

    fun pr(a: T?) {
        if (a is ByteArray) {
            println(a.contentToString())
        } else {
            println(a)
        }
    }

    @Suppress("DestructuringDeclarationWithTooManyEntries")
    fun scanIndexedEdges(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)

        // temporary fix for due date
        // { -- this will be removed
        val withOffset = stats.contains(StatKey.OFFSET)

        // if OFFSET exists, all options are ignored.
        if (withAll) {
            self.log.warn("WITH_ALL is ignored when OFFSET is present")
        }
        // }

        val srcPrefixStart = makeIndexedEdgeScanKeys(scanFilter)
        val rowsWithLastRow =
            Flux
                .fromIterable(srcPrefixStart)
                .flatMap { (src, prefix, start, end) ->
                    val marginLimit = scanFilter.limit.coerceAtLeast(scanFilter.limit + 1)
                    self
                        .scanStorage(prefix, marginLimit, start, end)
                        .map { marginGroup ->
                            // group by key
                            val hasNext = marginGroup.size > scanFilter.limit
                            val group = marginGroup.take(scanFilter.limit)
                            val rows =
                                group.mapNotNull {
                                    try {
                                        val schemaEdge = self.encodedEdgeToSchemaEdge(it)
                                        if (withOffset) {
                                            schemaEdge.toRowWithOffset(self.coder.encodeOffset(it), self.isMultiEdge)
                                        } else {
                                            schemaEdge.toRow(withAll, self.isMultiEdge)
                                        }
                                    } catch (e: Throwable) {
                                        self.log.error("Exception while decoding edge", e.message)
                                        null
                                    }
                                }
                            val lastValue = group.lastOrNull()
                            val offset = lastValue?.let { self.coder.encodeOffset(it) }
                            ScanResult(rows, offset, hasNext)
                        }
                }.collectList()

        return rowsWithLastRow.map {
            val rows = it.flatMap { pair -> pair.rows.take(scanFilter.limit) }
            val offsets = it.map { pair -> pair.offset }
            val hasNext = it.map { pair -> pair.hasNext }

            DataFrame(
                rows,
                when {
                    withOffset -> self.entity.schema.offsetStructType
                    withAll -> self.entity.schema.allStructType
                    else -> self.entity.schema.structType
                },
                offsets = offsets,
                hasNext = hasNext,
            )
        }
    }
}

data class EncodedStartStop<T>(
    val start: EncodedKey<T>,
    val stop: EncodedKey<T>?,
)

data class StartStop(
    val start: StartStopItem?,
    val stop: StartStopItem?,
) {
    fun ensureType(structType: StructType): StartStop {
        val start = start?.ensureType(structType)
        val stop = stop?.ensureType(structType)
        return StartStop(start = start, stop = stop)
    }
}

data class StartStopItem(
    val field: Index.Field,
    val inclusive: Boolean,
    val value: Any,
) {
    fun ensureType(type: StructType): StartStopItem {
        val dataType = type.nameToField[field.name]?.type ?: error("Field not found: ${field.name}")
        val castedValue = dataType.castNotNull(value)
        return copy(value = castedValue)
    }
}

data class RangeKey<T>(
    val src: Any,
    val prefix: EncodedKey<T>,
    val start: EncodedKey<T>?,
    val stop: EncodedKey<T>?,
)
