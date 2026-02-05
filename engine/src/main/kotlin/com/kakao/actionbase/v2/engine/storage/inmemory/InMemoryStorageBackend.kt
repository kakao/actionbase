package com.kakao.actionbase.v2.engine.storage.inmemory

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import java.util.concurrent.ConcurrentHashMap

import reactor.core.publisher.Mono

class InMemoryStorageBackend : StorageBackend {
    private val buckets = ConcurrentHashMap<String, InMemoryStorageBucket>()

    override fun buckets(): Mono<StorageBuckets> = Mono.fromCallable { InMemoryStorageBuckets(buckets.keys().toList().toSet()) }

    override fun bucket(name: String): Mono<StorageBucket> =
        Mono.fromCallable {
            buckets.computeIfAbsent(name) {
                InMemoryStorageBucket(name)
            }
        }
}
