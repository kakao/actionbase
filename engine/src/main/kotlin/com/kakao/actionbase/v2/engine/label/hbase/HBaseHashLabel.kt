package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
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
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBucket

import java.nio.ByteBuffer
import java.util.Arrays

import org.apache.hadoop.hbase.CellUtil
import org.apache.hadoop.hbase.CompareOperator
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.filter.BinaryComparator
import org.apache.hadoop.hbase.filter.FilterList
import org.apache.hadoop.hbase.filter.QualifierFilter
import org.apache.hadoop.hbase.filter.ValueFilter
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

open class HBaseHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    private val buckets: Mono<StorageBuckets>,
) : AbstractLabel<ByteArray>(entity, coder) {
    private val hbaseRecordCache: HBaseRecordCache = HBaseRecordCache.create()

    override fun findHashEdge(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return buckets
            .flatMap { it.edge.get(keyField.key) }
            .flatMap { value ->
                if (value == null) {
                    Mono.empty()
                } else {
                    Mono.just(value)
                }
            }
    }

    override fun create(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        return Mono.just(listOf(MutationRequest.Put(keyField.key, value)))
    }

    override fun update(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> = create(keyField, value)

    override fun delete(keyField: EncodedKey<ByteArray>): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        return Mono.just(listOf(MutationRequest.Delete(keyField.key)))
    }

    override fun handleDeferredRequests(deferredRequests: List<Any>): Mono<Boolean> =
        buckets.flatMap { storageBuckets ->
            val mutationRequests = deferredRequests.filterIsInstance<MutationRequest>()
            val hbaseMutations =
                deferredRequests.filter { request ->
                    request is Put || request is Delete || request is Increment
                }

            val operations = mutableListOf<Mono<Void>>()

            if (mutationRequests.isNotEmpty()) {
                operations += storageBuckets.edge.batch(mutationRequests)
            }

            if (hbaseMutations.isNotEmpty()) {
                val hbaseBucket =
                    storageBuckets.edge as? HBaseStorageBucket
                        ?: throw IllegalArgumentException("HBase mutations require HBaseStorageBucket")
                operations += hbaseBucket.batchRaw(hbaseMutations)
            }

            if (operations.isEmpty()) {
                Mono.just(true)
            } else {
                Mono.`when`(operations).thenReturn(true)
            }
        }

    override fun setnx(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return buckets.flatMap { it.edge.setIfNotExists(keyField.key, value) }
    }

    override fun setnxOnLock(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return buckets.flatMap { it.lock.setIfNotExists(keyField.key, value) }
    }

    override fun cad(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        return buckets
            .flatMap { it.lock.deleteIfEquals(keyField.key, value) }
            .map { isSuccess -> if (isSuccess) 1L else 0L }
    }

    override fun findLockValue(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return buckets
            .flatMap { it.lock.get(keyField.key) }
            .flatMap { value -> if (value == null) Mono.empty() else Mono.just(value) }
    }

    override fun incrby(
        key: ByteArray,
        acc: Long,
    ): Mono<List<Any>> = Mono.just(listOf(MutationRequest.Increment(key, acc)))

    // --- for scan

    override fun scanStorage(
        prefix: EncodedKey<ByteArray>,
        limit: Int,
        start: EncodedKey<ByteArray>?,
        end: EncodedKey<ByteArray>?,
    ): Mono<List<KeyFieldValue<ByteArray>>> {
        // inclusive false is not working
        // we need limit + 1 and drop the first element
        return buckets
            .flatMap { it.edge.scan(prefix.key, limit + 1, start?.key, end?.key) }
            .map { records ->
                records
                    .dropWhile { record -> start?.key?.let { key -> Arrays.compareUnsigned(key, record.key) >= 0 } ?: false }
                    .dropLastWhile { record -> end?.key?.let { key -> Arrays.compareUnsigned(key, record.key) < 0 } ?: false }
                    .take(limit)
                    .map { record -> KeyFieldValue(record.key, record.value) }
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

        val encodedKeys =
            src.map {
                val edge = Edge(0L, it, it).ensureType(entity.schema)
                coder.encodeHashEdgeKey(edge, entity.id)
            }

        val rows =
            buckets
                .flatMap { it.edge.get(encodedKeys.map { key -> key.key }) }
                .mapNotNull { records ->
                    val recordByKey = recordMap(records)
                    encodedKeys
                        .mapNotNull { encodedKey ->
                            val record = recordByKey[wrapKey(encodedKey.key)] ?: return@mapNotNull null
                            val schemaEdge = encodedEdgeToSchemaEdge(KeyFieldValue(record.key, record.value))
                            if (!withAll && !schemaEdge.isActive) {
                                null
                            } else {
                                if (withEdgeId) {
                                    schemaEdge.toRow(withAll, idEdgeEncoder)
                                } else {
                                    schemaEdge.toRow(withAll, null)
                                }
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

        val encodedKeys =
            tgt.map {
                val edge = Edge(0L, src, it).ensureType(entity.schema)
                coder.encodeHashEdgeKey(edge, entity.id)
            }

        val rows =
            buckets
                .flatMap { it.edge.get(encodedKeys.map { key -> key.key }) }
                .mapNotNull { records ->
                    val recordByKey = recordMap(records)
                    encodedKeys
                        .mapNotNull { encodedKey ->
                            val record = recordByKey[wrapKey(encodedKey.key)] ?: return@mapNotNull null
                            val schemaEdge = encodedEdgeToSchemaEdge(KeyFieldValue(record.key, record.value))
                            if (!withAll && !schemaEdge.isActive) {
                                null
                            } else {
                                if (withEdgeId) {
                                    schemaEdge.toRow(withAll, idEdgeEncoder, isMultiEdge)
                                } else {
                                    schemaEdge.toRow(withAll, null, isMultiEdge)
                                }
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
            buckets
                .flatMap { buckets ->
                    val edgeBucket =
                        buckets.edge as? HBaseStorageBucket
                            ?: throw IllegalArgumentException("HBaseStorageBucket is required for Get-based reads.")
                    edgeBucket.getRaw(gets)
                }.mapNotNull { results ->
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
        val keys = srcAndKeys.map { it.second }
        return buckets
            .flatMap { it.edge.get(keys) }
            .map { records ->
                val recordByKey = recordMap(records)
                srcAndKeys.map { (src, key) ->
                    val record = recordByKey[wrapKey(key)]
                    val count = record?.let { Bytes.toLong(it.value) } ?: 0L
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

        return buckets.flatMap { storageBuckets ->
            val edgeBucket = storageBuckets.edge
            if (edgeBucket is HBaseStorageBucket) {
                edgeBucket.getRaw(gets).map { results ->
                    results.flatMap { result ->
                        val cells = result.listCells() ?: return@flatMap emptyList()
                        cells.map { cell ->
                            val row = CellUtil.cloneRow(cell)
                            val qualifier = CellUtil.cloneQualifier(cell)
                            val value = CellUtil.cloneValue(cell)
                            HBaseRecord(row, qualifier, value)
                        }
                    }
                }
            } else {
                edgeBucket.get(rows).map { records ->
                    records.map { record -> HBaseRecord(record.key, record.value) }
                }
            }
        }
    }

    private fun recordMap(records: List<HBaseRecord>): Map<ByteBuffer, HBaseRecord> = records.associateBy { wrapKey(it.key) }

    private fun wrapKey(key: ByteArray): ByteBuffer = ByteBuffer.wrap(key)

    companion object : LabelFactory<HBaseHashLabel, HBaseStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: HBaseStorage,
            block: HBaseHashLabel.() -> Unit,
        ): HBaseHashLabel {
            val buckets = storage.options.getBuckets()
            return HBaseHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                buckets = buckets,
            )
        }
    }
}
