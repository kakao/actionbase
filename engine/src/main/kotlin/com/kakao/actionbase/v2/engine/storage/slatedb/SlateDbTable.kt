package com.kakao.actionbase.v2.engine.storage.slatedb

import io.slatedb.SlateDb
import io.slatedb.SlateDbKeyValue
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

    fun scanPrefix(
        prefix: ByteArray,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>>

    companion object {
        fun create(db: SlateDb): SlateDbTable = SlateDbTableImpl(db)
    }
}

internal class SlateDbTableImpl(
    private val db: SlateDb,
) : SlateDbTable {
    override fun get(key: ByteArray): Mono<ByteArray> =
        Mono
            .fromCallable { db.get(key) }
            .flatMap { Mono.justOrEmpty(it) }
            .subscribeOn(Schedulers.boundedElastic())

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> =
        Mono
            .fromCallable { db.put(key, value) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun delete(key: ByteArray): Mono<Void> =
        Mono
            .fromCallable { db.delete(key) }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun flush(): Mono<Void> =
        Mono
            .fromCallable { db.flush() }
            .subscribeOn(Schedulers.boundedElastic())
            .then()

    override fun scanPrefix(
        prefix: ByteArray,
        limit: Int,
    ): Mono<List<Pair<ByteArray, ByteArray>>> =
        Mono
            .fromCallable {
                val results = mutableListOf<Pair<ByteArray, ByteArray>>()
                db.scanPrefix(prefix).use { iterator ->
                    var kv: SlateDbKeyValue? = iterator.next()
                    var count = 0
                    while (kv != null && count < limit) {
                        results.add(kv.key() to kv.value())
                        count++
                        kv = iterator.next()
                    }
                }
                results.toList()
            }.subscribeOn(Schedulers.boundedElastic())

    override fun close() {
        db.close()
    }
}
