package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.StorageBucket

import reactor.core.publisher.Mono

class MemoryStorageBucket(
    private val store: ByteArrayStore,
) : StorageBucket {
    override fun get(key: ByteArray): Mono<ByteArray?> = Mono.fromCallable { store[key] }

    override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> =
        Mono.fromCallable {
            keys.mapNotNull { key -> store[key]?.let { HBaseRecord(key = key, value = it) } }
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
    ): Mono<List<HBaseRecord>> = Mono.fromCallable { store.prefixScan(prefix).take(limit) }

    override fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long> = Mono.fromCallable { store.increment(key, delta) }

    override fun batch(requests: List<MutationRequest>): Mono<Void> =
        Mono
            .fromCallable {
                requests.forEach { request ->
                    when (request) {
                        is MutationRequest.Put -> store[request.key] = request.value
                        is MutationRequest.Delete -> store.remove(request.key)
                        is MutationRequest.Increment -> store.increment(request.key, request.value)
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
