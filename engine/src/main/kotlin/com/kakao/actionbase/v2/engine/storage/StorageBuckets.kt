package com.kakao.actionbase.v2.engine.storage

import reactor.core.publisher.Mono

interface StorageBuckets {
    fun names(): Mono<Set<String>>
}
