package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.engine.storage.StorageOpCollector
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

import reactor.core.publisher.Mono

open class ByteArrayHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    protected val store: ByteArrayStore,
) : AbstractLabel<ByteArray>(entity, coder) {
    override fun findHashEdge(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return Mono.fromCallable { store[keyField.key] }.mapNotNull { it }
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
        Mono
            .fromCallable {
                deferredRequests.forEach { request ->
                    when (request) {
                        is MutationRequest.Put -> store[request.key] = request.value
                        is MutationRequest.Delete -> store.remove(request.key)
                        is MutationRequest.Increment -> store.increment(request.key, request.value)
                        else -> throw IllegalArgumentException("Unsupported request type: $request")
                    }
                }
                true
            }

    override fun setnx(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return Mono.fromCallable { store.checkAndSet(keyField.key, null, value) }
    }

    override fun setnxOnLock(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return Mono.fromCallable { store.checkAndSet(keyField.key, null, value) }
    }

    override fun cad(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        return Mono.fromCallable { if (store.checkAndSet(keyField.key, value, null)) 1L else 0L }
    }

    override fun findLockValue(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return Mono.fromCallable { store[keyField.key] }.mapNotNull { it }
    }

    override fun deleteOnLock(keyField: KeyValue<ByteArray>): Mono<Boolean> = cad(EncodedKey(keyField.key), keyField.value).map { it > 0 }

    override fun scanStorage(
        prefix: EncodedKey<ByteArray>,
        limit: Int,
        start: EncodedKey<ByteArray>?,
        end: EncodedKey<ByteArray>?,
    ): Mono<List<KeyFieldValue<ByteArray>>> =
        Mono.fromCallable {
            store
                .prefixScan(prefix.key)
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
        return Mono
            .fromCallable {
                src
                    .mapNotNull {
                        val edge = Edge(0L, it, it).ensureType(entity.schema)
                        val key = coder.encodeHashEdgeKey(edge, entity.id)
                        store[key.key]?.let { value -> encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value)) }
                    }.filter { withAll || it.isActive }
                    .map { it.toRow(withAll) }
            }.map { rows -> toDataFrame(rows, withAll) }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        return Mono
            .fromCallable {
                tgt
                    .mapNotNull {
                        val edge = Edge(0L, src, it).ensureType(entity.schema)
                        val key = coder.encodeHashEdgeKey(edge, entity.id)
                        store[key.key]?.let { value -> encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value)) }
                    }.filter { withAll || it.isActive }
                    .map { it.toRow(withAll, isMultiEdge) }
            }.map { rows -> toDataFrame(rows, withAll) }
    }

    override fun getCountRows(
        srcAndKeys: List<Pair<Any, ByteArray>>,
        dir: Direction,
    ): Mono<List<Row>> =
        Mono.fromCallable {
            srcAndKeys.map { (src, key) ->
                val value = store[key]
                val count = if (value == null) 0L else ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long
                Row(arrayOf(src, count, dir))
            }
        }

    private fun toDataFrame(
        rows: List<Row>,
        withAll: Boolean,
    ): DataFrame =
        DataFrame(
            rows,
            if (withAll) entity.schema.allStructType else entity.schema.structType,
        )
}
