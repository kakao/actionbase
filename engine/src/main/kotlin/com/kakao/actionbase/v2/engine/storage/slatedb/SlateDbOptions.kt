package com.kakao.actionbase.v2.engine.storage.slatedb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import reactor.core.publisher.Mono

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlateDbOptions(
    val path: String = "data",
    val url: String = "",
) {
    fun checkConnection(): Mono<Boolean> =
        if (url.isBlank()) {
            Mono.just(false)
        } else {
            Mono.just(true)
        }

    fun getTable(): Mono<SlateDbTable> =
        SlateDbConnections.getConnection(
            dbPath = path,
            url = url,
        )
}
