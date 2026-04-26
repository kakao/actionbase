package com.kakao.actionbase.v2.engine.storage.slatedb

import java.nio.ByteBuffer
import java.nio.ByteOrder

import io.slatedb.uniffi.Db
import io.slatedb.uniffi.DbIterator
import io.slatedb.uniffi.MergeOperator
import io.slatedb.uniffi.WriteBatch
import reactor.core.publisher.Mono

fun Long.toSlateBytes(): ByteArray =
    ByteBuffer
        .allocate(Long.SIZE_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(this)
        .array()

fun ByteArray.toLong(): Long = ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).long

val incrementMergeOperator =
    MergeOperator { _, existingValue, operand ->
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

interface SlateDbTable {
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

    fun close(): Mono<Void>

    companion object {
        fun create(db: Db): SlateDbTable = SlateDbTableImpl(db)
    }
}

internal class SlateDbTableImpl(
    private val db: Db,
) : SlateDbTable {
    // All `Mono.fromFuture { db.<op>(..) }` calls use the Supplier overload so
    // the underlying CompletableFuture is created at subscription time, not
    // when the Mono is constructed. The eager overload would start every
    // operation in a chain like `put.then(delete).then(get)` immediately,
    // racing them against each other.

    override fun get(key: ByteArray): Mono<ByteArray> =
        Mono.fromFuture { db.get(key) }.flatMap { Mono.justOrEmpty(it) }

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> = Mono.fromFuture { db.put(key, value) }.then()

    override fun delete(key: ByteArray): Mono<Void> = Mono.fromFuture { db.delete(key) }.then()

    override fun merge(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> = Mono.fromFuture { db.merge(key, value) }.then()

    override fun flush(): Mono<Void> = Mono.fromFuture { db.flush() }

    override fun scanPrefix(
        prefix: ByteArray,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>> =
        Mono
            .fromFuture { db.scanPrefix(prefix) }
            .flatMap { iterator -> drainIterator(iterator, limit) }

    // DbIterator.next() returns CompletableFuture<KeyValue?>; null marks end-of-stream.
    // Drain by chaining continuations rather than .get(), so the calling thread is
    // never blocked while UniFFI's Tokio runtime fetches the next block.
    private fun drainIterator(
        iterator: DbIterator,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>> =
        Mono
            .create<List<Pair<ByteArray, ByteArray>>> { sink ->
                val results = mutableListOf<Pair<ByteArray, ByteArray>>()
                fun pump() {
                    if (results.size >= limit) {
                        sink.success(results.toList())
                        return
                    }
                    iterator.next().whenComplete { kv, err ->
                        when {
                            err != null -> sink.error(err)
                            kv == null -> sink.success(results.toList())
                            else -> {
                                results.add(kv.key() to kv.value())
                                pump()
                            }
                        }
                    }
                }
                pump()
            }.doFinally { iterator.close() }

    override fun batch(operations: List<BatchOperation>): Mono<Void> =
        Mono
            .defer {
                // Fresh batch per subscription (db.write consumes it). doFinally close
                // pins it across the async write so UniFFI's cleaner cannot free the
                // Rust handle mid-flight; close is idempotent.
                val batch = WriteBatch()
                operations.forEach { op ->
                    when (op) {
                        is BatchOperation.Put -> batch.put(op.key, op.value)
                        is BatchOperation.Delete -> batch.delete(op.key)
                        is BatchOperation.Increment -> batch.merge(op.key, op.delta.toSlateBytes())
                    }
                }
                Mono.fromFuture { db.write(batch) }.doFinally { batch.close() }
            }.then()

    override fun close(): Mono<Void> = Mono.fromFuture { db.shutdown() }
}
