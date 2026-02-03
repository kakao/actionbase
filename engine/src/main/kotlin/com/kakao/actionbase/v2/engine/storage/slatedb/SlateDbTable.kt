package com.kakao.actionbase.v2.engine.storage.slatedb

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

interface SlateDbTable : AutoCloseable {
    fun get(key: ByteArray): Mono<ByteArray>

    fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void>

    fun delete(key: ByteArray): Mono<Void>

    fun flush(): Mono<Void>

    companion object {
        fun create(native: SlateDbNative): SlateDbTable = SlateDbTableImpl(native)
    }
}

internal class SlateDbTableImpl(
    private val native: SlateDbNative,
) : SlateDbTable {
    override fun get(key: ByteArray): Mono<ByteArray> =
        Mono
            .fromCallable { native.get(key) }
            .flatMap { Mono.justOrEmpty(it) }
            .subscribeOn(Schedulers.boundedElastic())

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> =
        Mono
            .fromCallable { native.put(key, value) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun delete(key: ByteArray): Mono<Void> =
        Mono
            .fromCallable { native.delete(key) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun flush(): Mono<Void> =
        Mono
            .fromCallable { native.flush() }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun close() {
        native.close()
    }
}
