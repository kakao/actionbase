package com.kakao.actionbase.v2.engine.storage

import reactor.core.publisher.Mono

interface StorageBackend {
    fun buckets(): Mono<StorageBuckets>

    fun bucket(name: String): Mono<StorageBucket>
}
