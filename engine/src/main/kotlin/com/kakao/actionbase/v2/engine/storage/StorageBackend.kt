package com.kakao.actionbase.v2.engine.storage

import reactor.core.publisher.Mono
import java.lang.AutoCloseable

interface StorageBackend : AutoCloseable {
    fun getBucket(namespace: String, name: String): Mono<StorageBuckets>
    fun getBucket(uri: String): Mono<StorageBuckets>
}