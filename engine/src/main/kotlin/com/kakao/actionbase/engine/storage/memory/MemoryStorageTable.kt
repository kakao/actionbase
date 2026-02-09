package com.kakao.actionbase.engine.storage.memory

import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.engine.storage.StorageTable

import reactor.core.publisher.Mono

class MemoryStorageTable(
    private val store: ByteArrayStore,
) : StorageTable {
    override fun get(key: ByteArray): Mono<ByteArray?> = Mono.fromCallable { store[key] }

    override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> =
        Mono.fromCallable {
            keys.mapNotNull { k -> store[k]?.let { HBaseRecord(key = k, value = it) } }
        }

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> = Mono.fromCallable { store[key] = value }.then()

    override fun delete(key: ByteArray): Mono<Void> = Mono.fromCallable { store.remove(key) }.then()

    override fun scan(
        prefix: ByteArray,
        limit: Int,
        start: ByteArray?,
        stop: ByteArray?,
    ): Mono<List<HBaseRecord>> =
        Mono.fromCallable {
            store
                .prefixScan(prefix)
                .filter { record ->
                    val afterStart = start == null || compareByteArrays(record.key, start) >= 0
                    val beforeStop = stop == null || compareByteArrays(record.key, stop) < 0
                    afterStart && beforeStop
                }.take(limit)
        }

    private fun compareByteArrays(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    override fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long> = Mono.fromCallable { store.increment(key, delta) }

    override fun batch(requests: List<MutationRequest>): Mono<Void> =
        Mono
            .fromCallable {
                requests.forEach {
                    when (it) {
                        is MutationRequest.Put -> store[it.key] = it.value
                        is MutationRequest.Delete -> store.remove(it.key)
                        is MutationRequest.Increment -> store.increment(it.key, it.value)
                    }
                }
            }.then()

    override fun exists(key: ByteArray): Mono<Boolean> = Mono.fromCallable { store[key] != null }

    override fun setIfNotExists(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Boolean> = Mono.fromCallable { store.checkAndSet(key, null, value) }

    override fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Mono<Boolean> = Mono.fromCallable { store.checkAndSet(key, expectedValue, null) }
}
