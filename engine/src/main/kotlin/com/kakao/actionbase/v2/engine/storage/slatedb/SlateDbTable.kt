package com.kakao.actionbase.v2.engine.storage.slatedb

import java.nio.ByteBuffer
import java.nio.ByteOrder

import io.slatedb.SlateDb
import io.slatedb.SlateDbKeyValue
import io.slatedb.SlateDbMergeOperator
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

fun Long.toSlateBytes(): ByteArray =
    ByteBuffer
        .allocate(Long.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(this)
        .array()

fun ByteArray.toLong(): Long = ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).long

val incrementMergeOperator =
    SlateDbMergeOperator { _, existingValue, operand ->
        val current = existingValue?.toLong() ?: 0L
        val delta = operand.toLong()
        (current + delta).toSlateBytes()
    }

sealed class BatchOperation {
    data class Put(
        val key: ByteArray,
        val value: ByteArray,
    ) : BatchOperation()

    data class Delete(
        val key: ByteArray,
    ) : BatchOperation()

    data class Increment(
        val key: ByteArray,
        val delta: Long,
    ) : BatchOperation()
}

interface SlateDbTable : AutoCloseable {
    fun get(key: ByteArray): Mono<ByteArray>

    fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void>

    fun delete(key: ByteArray): Mono<Void>

    fun merge(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void>

    fun flush(): Mono<Void>

    fun scanPrefix(
        prefix: ByteArray,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>>

    fun batch(operations: List<BatchOperation>): Mono<Void>

    companion object {
        fun create(db: SlateDb): SlateDbTable = SlateDbTableImpl(db)
    }
}

internal class SlateDbTableImpl(
    private val db: SlateDb,
) : SlateDbTable {
    override fun get(key: ByteArray): Mono<ByteArray> =
        Mono
            .fromCallable { db.get(key) }
            .flatMap { Mono.justOrEmpty(it) }
            .subscribeOn(Schedulers.boundedElastic())

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> =
        Mono
            .fromCallable { db.put(key, value) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun delete(key: ByteArray): Mono<Void> =
        Mono
            .fromCallable { db.delete(key) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun merge(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> =
        Mono
            .fromCallable { db.merge(key, value) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun flush(): Mono<Void> =
        Mono
            .fromCallable { db.flush() }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun scanPrefix(
        prefix: ByteArray,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>> =
        Mono
            .fromCallable {
                val results = mutableListOf<Pair<ByteArray, ByteArray>>()
                db.scanPrefix(prefix).use { iterator ->
                    var kv: SlateDbKeyValue? = iterator.next()
                    var count = 0
                    while (kv != null && count < limit) {
                        results.add(kv.key() to kv.value())
                        count++
                        kv = iterator.next()
                    }
                }
                results.toList()
            }.subscribeOn(Schedulers.boundedElastic())

    override fun batch(operations: List<BatchOperation>): Mono<Void> =
        Mono
            .fromCallable {
                SlateDb.newWriteBatch().use { batch ->
                    operations.forEach { op ->
                        when (op) {
                            is BatchOperation.Put -> batch.put(op.key, op.value)
                            is BatchOperation.Delete -> batch.delete(op.key)
                            is BatchOperation.Increment -> batch.merge(op.key, op.delta.toSlateBytes())
                        }
                    }
                    db.write(batch)
                }
            }.subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun close() {
        db.close()
    }
}
