package com.kakao.actionbase.v2.engine.storage.slatedb

import com.kakao.actionbase.v2.engine.util.getLogger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import io.slatedb.SlateDb
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

object SlateDbConnections {
    private val logger = getLogger()

    private val libraryLoaded = AtomicBoolean(false)
    private val connections: ConcurrentHashMap<String, Mono<SlateDbTable>> = ConcurrentHashMap()

    fun loadLibrary(libraryPath: String) {
        if (libraryLoaded.compareAndSet(false, true)) {
            logger.info("Loading SlateDB native library from: {}", libraryPath)
            SlateDb.loadLibrary(libraryPath)
            SlateDb.initLogging("info")
        }
    }

    fun getConnection(
        dbPath: String,
        url: String,
        libraryPath: String,
    ): Mono<SlateDbTable> {
        val cacheKey = getCacheKey(dbPath, url)

        return connections.computeIfAbsent(cacheKey) { key ->
            Mono
                .fromCallable {
                    loadLibrary(libraryPath)
                    val db = SlateDb.open(dbPath, url, null)
                    SlateDbTable.create(db)
                }.subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess {
                    logger.info("Successfully opened SlateDB connection for cacheKey: {}", key)
                }.doOnError { error ->
                    logger.error("Failed to open SlateDB connection for cacheKey: {}", key, error)
                    connections.remove(key)
                }.cache()
        }
    }

    private fun getCacheKey(
        dbPath: String,
        url: String,
    ): String = "$url/$dbPath"

    fun closeConnections(): Mono<Void> {
        val closeMonos =
            connections.entries.map { (key, tableMono) ->
                tableMono
                    .flatMap { table ->
                        Mono
                            .fromRunnable<Void> {
                                try {
                                    table.close()
                                    logger.info("Closed SlateDB connection for cacheKey: {}", key)
                                } catch (e: Exception) {
                                    logger.error("Error closing SlateDB connection for cacheKey: {}", key, e)
                                }
                            }.subscribeOn(Schedulers.boundedElastic())
                    }
            }
        return Mono.`when`(closeMonos).doFinally { connections.clear() }
    }
}
