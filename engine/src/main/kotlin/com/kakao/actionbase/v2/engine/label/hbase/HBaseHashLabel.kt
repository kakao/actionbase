package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.engine.util.HBaseRecordCache
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.edge.decodeByteArray
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import java.util.Arrays

import org.apache.hadoop.hbase.CellUtil
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.ColumnCountGetFilter
import org.apache.hadoop.hbase.filter.ColumnRangeFilter
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.PageFilter
import org.apache.hadoop.hbase.filter.QualifierFilter
import org.apache.hadoop.hbase.filter.ValueFilter
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

open class HBaseHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    private val tables: Mono<HBaseTables>,
) : AbstractLabel<ByteArray>(entity, coder) {
    private val hbaseRecordCache: HBaseRecordCache = HBaseRecordCache.create()

    override fun findHashEdge(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        val get =
            Get(keyField.key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        val result = tables.flatMap { it.edge.get(get) }
        return result.mapNotNull {
            if (it.isEmpty) {
                null
            } else {
                it.value()
            }
        }
    }

    override fun create(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        val put =
            Put(keyField.key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
        // return tables.flatMap { it.edge.put(put) }
        //    .thenReturn(true)
        return Mono.just(listOf(put))
    }

    override fun update(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> = create(keyField, value)

    override fun delete(keyField: EncodedKey<ByteArray>): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        val delete = Delete(keyField.key)
        // return tables.flatMap { it.edge.delete(delete) }.thenReturn(true)
        return Mono.just(listOf(delete))
    }

    override fun handleDeferredRequests(deferredRequests: List<Any>): Mono<Boolean> = tables.flatMap { it.edge.batch(deferredRequests) }.thenReturn(true)

    override fun setnx(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        val request =
            CheckAndMutate
                .newBuilder(keyField.key)
                .ifNotExists(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .build(Put(keyField.key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value))
        return tables.flatMap { it.edge.checkAndMutate(request) }.map {
            it.isSuccess
        }
    }

    override fun setnxOnLock(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        val request =
            CheckAndMutate
                .newBuilder(keyField.key)
                .ifNotExists(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .build(Put(keyField.key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value))
        return tables.flatMap { it.lock.checkAndMutate(request) }.map {
            it.isSuccess
        }
    }

    override fun cad(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        val request =
            CheckAndMutate
                .newBuilder(keyField.key)
                .ifEquals(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
                .build(Delete(keyField.key))
        return tables.flatMap { tables ->
            tables.lock
                .checkAndMutate(request)
                .map { result ->
                    result.isSuccess
                }.map { isSuccess -> if (isSuccess) 1L else 0L }
        }
    }

    override fun findLockValue(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        val get =
            Get(keyField.key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        return tables.flatMap {
            it.lock.get(get).mapNotNull { result ->
                if (result.isEmpty) {
                    null
                } else {
                    result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                }
            }
        }
    }

    override fun incrby(
        key: ByteArray,
        acc: Long,
    ): Mono<List<Any>> {
        val increment =
            Increment(key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, acc)
        // return tables.flatMap { it.edge.increment(increment) }.map {
        //     Bytes.toLong(it.value())
        // }
        return Mono.just(listOf(increment))
    }

    // --- for scan

    override fun scanStorage(
        prefix: EncodedKey<ByteArray>,
        limit: Int,
        start: EncodedKey<ByteArray>?,
        end: EncodedKey<ByteArray>?,
    ): Mono<List<KeyFieldValue<ByteArray>>> {
        // inclusive false is not working
        // we need limit + 1 and drop the first element
        val scan =
            Scan()
                .setRowPrefixFilter(prefix.key)
                .setFilter(PageFilter((limit + 1).toLong())) // plus 1 for the inclusive start row

        start?.let { scan.withStartRow(it.key, false) }

        end?.let { scan.withStopRow(it.key, false) }

        scan.addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)

        return tables
            .flatMap { it.edge.scan(scan, limit + 1) } // plus 1 for the inclusive start row
            .map {
                it
                    // Search for rows greater than or equal to start key and offset
                    .dropWhile { result -> start?.key?.let { key -> Arrays.compareUnsigned(key, result.row) >= 0 } ?: false }
                    // Search for rows less than end key
                    .dropLastWhile { result -> end?.key?.let { key -> Arrays.compareUnsigned(key, result.row) < 0 } ?: false }
                    .take(limit)
                    .map { result ->
                        KeyFieldValue(result.row, result.value())
                    }
            }
    }

    override fun encodedEdgeToSchemaEdge(keyFieldValue: KeyFieldValue<ByteArray>): SchemaEdge = entity.schema.decodeByteArray(keyFieldValue)

    override fun deleteOnLock(keyField: KeyValue<ByteArray>): Mono<Boolean> = cad(EncodedKey(keyField.key), keyField.value).map { it > 0 }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val withEdgeId = withAll || stats.contains(StatKey.EDGE_ID)

        val gets =
            src.map {
                val edge = Edge(0L, it, it).ensureType(entity.schema)
                val key = coder.encodeHashEdgeKey(edge, entity.id)
                Get(key.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }

        val rows =
            tables
                .flatMap { it.edge.get(gets) }
                .mapNotNull { results ->
                    results
                        .map {
                            if (it.isEmpty) {
                                null
                            } else {
                                encodedEdgeToSchemaEdge(KeyFieldValue(it.row, it.value()))
                            }
                        }.filter { it != null && (withAll || it.isActive) }
                        .map {
                            if (withEdgeId) {
                                it!!.toRow(withAll, idEdgeEncoder)
                            } else {
                                it!!.toRow(withAll, null)
                            }
                        }
                }

        return rows
            .map {
                DataFrame(
                    it,
                    if (withAll) {
                        entity.schema.allStructType
                    } else if (withEdgeId) {
                        entity.schema.edgeIdStructType
                    } else {
                        entity.schema.structType
                    },
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val withEdgeId = withAll || stats.contains(StatKey.EDGE_ID)

        val gets =
            tgt.map {
                val edge = Edge(0L, src, it).ensureType(entity.schema)
                val key = coder.encodeHashEdgeKey(edge, entity.id)
                Get(key.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }

        val rows =
            tables
                .flatMap { it.edge.get(gets) }
                .mapNotNull { results ->
                    results
                        .map {
                            if (it.isEmpty) {
                                null
                            } else {
                                encodedEdgeToSchemaEdge(KeyFieldValue(it.row, it.value()))
                            }
                        }.filter { it != null && (withAll || it.isActive) }
                        .map {
                            if (withEdgeId) {
                                it!!.toRow(withAll, idEdgeEncoder, isMultiEdge)
                            } else {
                                it!!.toRow(withAll, null, isMultiEdge)
                            }
                        }
                }

        return rows
            .map {
                DataFrame(
                    it,
                    if (withAll) {
                        entity.schema.allStructType
                    } else if (withEdgeId) {
                        entity.schema.edgeIdStructType
                    } else {
                        entity.schema.structType
                    },
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    fun getActiveStates(gets: List<Get>): Mono<DataFrame> {
        val rows =
            tables
                .flatMap { it.edge.get(gets) }
                .mapNotNull { results ->
                    results
                        .map {
                            if (it.isEmpty) {
                                null
                            } else {
                                encodedEdgeToSchemaEdge(KeyFieldValue(it.row, it.value()))
                            }
                        }.filter { it != null && it.isActive }
                        .map {
                            it!!.toRow(withAll = false, null, isMultiEdge)
                        }
                }
        return rows
            .map {
                DataFrame(
                    it,
                    entity.schema.structType,
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    override fun getCountRows(
        srcAndKeys: List<Pair<Any, ByteArray>>,
        dir: Direction,
    ): Mono<List<Row>> {
        val gets =
            srcAndKeys.map {
                Get(
                    it.second,
                ).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }
        return tables
            .flatMap { it.edge.get(gets) }
            .map {
                srcAndKeys
                    .map { (src, _) -> src }
                    .zip(it)
                    .map { (src, result) ->
                        val count = if (result.isEmpty) 0L else Bytes.toLong(result.value())
                        Row(arrayOf(src, count, dir))
                    }
            }
    }

    fun getRawHashEdgeValueForTest(
        src: Any,
        tgt: Any,
    ): Mono<ByteArray> {
        val typedSrc =
            entity.schema.src.dataType
                .cast(src)
        val typedTgt =
            entity.schema.tgt.dataType
                .cast(tgt)
        val edge = Edge(0L, typedSrc, typedTgt)
        val encodedHashEdgeKey = coder.encodeHashEdgeKey(edge, entity.id)
        return findHashEdge(encodedHashEdgeKey)
    }

    fun hbaseGet(
        rows: List<ByteArray>,
        from: ByteArray,
        to: ByteArray,
        order: Order,
        ttl: Long? = null,
    ): Mono<Pair<List<HBaseRecord>, Int>> {
        if (ttl == null) {
            return hbaseGetDirect(rows, from, to, order).map { it to 0 }
        }

        val cachedResults = mutableMapOf<ByteArray, Mono<List<HBaseRecord>>>()
        val missingRows = mutableListOf<ByteArray>()

        // Query cache and collect missed items
        rows.forEach { row ->
            hbaseRecordCache.getIfNotExpired(row, from, to, order, ttl)?.let { cached ->
                cachedResults[row] = cached
            } ?: missingRows.add(row)
        }

        return if (missingRows.isEmpty()) {
            // All data is in cache
            combineResults(rows, cachedResults).map { it to rows.size }
        } else {
            // Batch query missed items and store in cache
            hbaseGetDirect(missingRows, from, to, order)
                .doOnNext { records ->
                    missingRows.forEach { row ->
                        val rowRecords = records.filter { it.key.contentEquals(row) }
                        val cachedMono = Mono.just(rowRecords)
                        hbaseRecordCache.put(row, from, to, order, cachedMono)
                        cachedResults[row] = cachedMono
                    }
                }.then(Mono.defer { combineResults(rows, cachedResults) })
                .map { it to (rows.size - missingRows.size) }
        }
    }

    private fun combineResults(
        rows: List<ByteArray>,
        cachedResults: Map<ByteArray, Mono<List<HBaseRecord>>>,
    ): Mono<List<HBaseRecord>> {
        val orderedMonos = rows.mapNotNull { row -> cachedResults[row] }

        return if (orderedMonos.isEmpty()) {
            Mono.just(emptyList())
        } else {
            Mono.zip(orderedMonos) { results ->
                results.flatMap { it as List<HBaseRecord> }
            }
        }
    }

    fun hbaseGetWideRow(
        rows: List<ByteArray>,
        from: ByteArray?,
        to: ByteArray?,
        order: Order,
        limit: Int,
    ): Mono<List<HBaseRecord>> {
        // ColumnRangeFilter requires minColumn <= maxColumn in byte order.
        // DESC encoding flips bytes, so from > to; swap to restore byte order.
        val (min, max) =
            if (order == Order.DESC) to to from else from to to

        val gets =
            rows.map {
                val get =
                    Get(it)
                        .addFamily(Constants.DEFAULT_COLUMN_FAMILY)

                val filters = FilterList(FilterList.Operator.MUST_PASS_ALL)

                if (min != null || max != null) {
                    filters.addFilter(
                        ColumnRangeFilter(
                            min,
                            false,
                            max,
                            false,
                        ),
                    )
                }

                // Exclude empty values (tombstones)
                filters.addFilter(
                    ValueFilter(
                        CompareOperator.NOT_EQUAL,
                        BinaryComparator(Bytes.toBytes("")),
                    ),
                )

                filters.addFilter(ColumnCountGetFilter(limit))

                get.setFilter(filters)
            }
        return tables.flatMap { it.edge.get(gets) }.map {
            it.flatMap { result ->
                val cells = result.listCells() ?: return@flatMap emptyList()
                cells.map { cell ->
                    val row = CellUtil.cloneRow(cell)
                    val qualifier = CellUtil.cloneQualifier(cell)
                    val value = CellUtil.cloneValue(cell)
                    HBaseRecord(row, qualifier, value)
                }
            }
        }
    }

    private fun hbaseGetDirect(
        rows: List<ByteArray>,
        from: ByteArray,
        to: ByteArray,
        order: Order,
    ): Mono<List<HBaseRecord>> {
        val gets =
            rows.map {
                val get =
                    Get(it)
                        .addFamily(Constants.DEFAULT_COLUMN_FAMILY)

                val complexFilter = FilterList(FilterList.Operator.MUST_PASS_ALL)

                if (order == Order.DESC) {
                    complexFilter.addFilter(
                        QualifierFilter(
                            CompareOperator.LESS_OR_EQUAL,
                            BinaryComparator(from),
                        ),
                    )
                    complexFilter.addFilter(
                        QualifierFilter(
                            CompareOperator.GREATER_OR_EQUAL,
                            BinaryComparator(to),
                        ),
                    )
                } else {
                    complexFilter.addFilter(
                        QualifierFilter(
                            CompareOperator.GREATER_OR_EQUAL,
                            BinaryComparator(from),
                        ),
                    )
                    complexFilter.addFilter(
                        QualifierFilter(
                            CompareOperator.LESS_OR_EQUAL,
                            BinaryComparator(to),
                        ),
                    )
                }

                // Add value condition (exclude empty values)
                complexFilter.addFilter(
                    ValueFilter(
                        CompareOperator.NOT_EQUAL,
                        BinaryComparator(Bytes.toBytes("")),
                    ),
                )
                get.setFilter(complexFilter)
            }
        return tables.flatMap { it.edge.get(gets) }.map {
            it.flatMap { result ->
                val cells = result.listCells() ?: return@flatMap emptyList()
                cells.map { cell ->
                    val row = CellUtil.cloneRow(cell)
                    val qualifier = CellUtil.cloneQualifier(cell)
                    val value = CellUtil.cloneValue(cell)
                    HBaseRecord(row, qualifier, value)
                }
            }
        }
    }

    companion object : LabelFactory<HBaseHashLabel, HBaseStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: HBaseStorage,
            block: HBaseHashLabel.() -> Unit,
        ): HBaseHashLabel {
            val tables = storage.options.getTables()
            return HBaseHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = tables,
            )
        }
    }
}
