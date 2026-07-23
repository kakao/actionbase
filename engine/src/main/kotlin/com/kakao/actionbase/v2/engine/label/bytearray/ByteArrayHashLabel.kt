package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.engine.storage.StorageTable
import com.kakao.actionbase.engine.storage.memory.MemoryStorageTable
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.edge.decodeByteArray
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class ByteArrayHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    protected val table: StorageTable,
) : AbstractLabel<ByteArray>(entity, coder) {
    constructor(
        entity: LabelEntity,
        coder: EdgeEncoder<ByteArray>,
        store: ByteArrayStore,
    ) : this(entity, coder, MemoryStorageTable(store))

    override fun findHashEdge(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return table.get(keyField.key).mapNotNull { it }
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

    override fun incrby(
        key: ByteArray,
        acc: Long,
    ): Mono<List<Any>> = Mono.just(listOf(MutationRequest.Increment(key, acc)))

    override fun handleDeferredRequests(
        deferredRequests: List<Any>,
        storageOpCollector: StorageOpCollector?,
    ): Mono<Boolean> =
        table
            .batch(
                deferredRequests.map {
                    it as? MutationRequest ?: throw IllegalArgumentException("Unsupported request type: $it")
                },
            ).thenReturn(true)

    override fun setnx(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return table.setIfNotExists(keyField.key, value)
    }

    override fun setnxOnLock(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return table.setIfNotExists(keyField.key, value)
    }

    override fun cad(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        return table.deleteIfEquals(keyField.key, value).map { if (it) 1L else 0L }
    }

    override fun findLockValue(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return table.get(keyField.key).mapNotNull { it }
    }

    override fun deleteOnLock(keyField: KeyValue<ByteArray>): Mono<Boolean> = cad(EncodedKey(keyField.key), keyField.value).map { it > 0 }

    override fun scanStorage(
        prefix: EncodedKey<ByteArray>,
        limit: Int,
        start: EncodedKey<ByteArray>?,
        end: EncodedKey<ByteArray>?,
    ): Mono<List<KeyFieldValue<ByteArray>>> =
        // The store scan is a pure prefix read; the start (exclusive) / end (inclusive) bounds are
        // applied here, not pushed to the backend, so the contract is identical across backends
        // regardless of the backend's own bound convention.
        table.scan(prefix.key, Int.MAX_VALUE, null, null).map { records ->
            records
                .dropWhile { record -> start?.key?.let { Arrays.compareUnsigned(it, record.key) >= 0 } ?: false }
                .dropLastWhile { record -> end?.key?.let { Arrays.compareUnsigned(it, record.key) < 0 } ?: false }
                .take(limit)
                .map { KeyFieldValue(it.key, it.value) }
        }

    override fun encodedEdgeToSchemaEdge(keyFieldValue: KeyFieldValue<ByteArray>): SchemaEdge = entity.schema.decodeByteArray(keyFieldValue)

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        return Flux
            .fromIterable(src)
            .concatMap {
                val edge = Edge(0L, it, it).ensureType(entity.schema)
                val key = coder.encodeHashEdgeKey(edge, entity.id)
                table.get(key.key).map { value -> encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value)) }
            }.filter { withAll || it.isActive }
            .map { it.toRow(withAll) }
            .collectList()
            .map { rows -> toDataFrame(rows, withAll) }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        return Flux
            .fromIterable(tgt)
            .concatMap {
                val edge = Edge(0L, src, it).ensureType(entity.schema)
                val key = coder.encodeHashEdgeKey(edge, entity.id)
                table.get(key.key).map { value -> encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value)) }
            }.filter { withAll || it.isActive }
            .map { it.toRow(withAll, isMultiEdge) }
            .collectList()
            .map { rows -> toDataFrame(rows, withAll) }
    }

    override fun getCountRows(
        srcAndKeys: List<Pair<Any, ByteArray>>,
        dir: Direction,
    ): Mono<List<Row>> =
        Flux
            .fromIterable(srcAndKeys)
            .concatMap { (src, key) ->
                table
                    .get(key)
                    .map { value -> Row(arrayOf(src, ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long, dir)) }
                    .defaultIfEmpty(Row(arrayOf(src, 0L, dir)))
            }.collectList()

    private fun toDataFrame(
        rows: List<Row>,
        withAll: Boolean,
    ): DataFrame =
        DataFrame(
            rows,
            if (withAll) entity.schema.allStructType else entity.schema.structType,
        )
}
