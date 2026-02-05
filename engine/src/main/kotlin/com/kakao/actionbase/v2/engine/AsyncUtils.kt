package com.kakao.actionbase.v2.engine

import java.util.concurrent.CompletableFuture

import reactor.core.publisher.Mono

object AsyncUtils {
    fun <T> asMono(future: CompletableFuture<T>): Mono<T> = Mono.fromFuture(future)
}
