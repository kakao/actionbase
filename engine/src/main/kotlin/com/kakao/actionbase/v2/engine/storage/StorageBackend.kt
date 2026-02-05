package com.kakao.actionbase.v2.engine.storage

import java.lang.AutoCloseable

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets>

    fun getBucket(uri: String): Mono<StorageBuckets>
}
