package com.kakao.actionbase.v2.engine.storage.inmemory

import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import reactor.core.publisher.Mono

class InMemoryStorageBuckets(
    private val names: Set<String>,
) : StorageBuckets {
    override fun names(): Mono<Set<String>> = Mono.fromCallable { names }
}
