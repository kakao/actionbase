package com.kakao.actionbase.v2.engine.storage.slatedb

import com.kakao.actionbase.v2.engine.util.getLogger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import io.slatedb.uniffi.DbBuilder
import io.slatedb.uniffi.LogLevel
import io.slatedb.uniffi.ObjectStore
import io.slatedb.uniffi.Slatedb
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

object SlateDbConnections {
    private val logger = getLogger()

    private val initialized = AtomicBoolean(false)
    private val connections: ConcurrentHashMap<String, Mono<SlateDbTable>> = ConcurrentHashMap()

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            extractSlateDbNativeLibrary()
            logger.info("Initializing SlateDB (UniFFI native library loaded from JAR classpath)")
            // The second argument is an optional foreign log callback; null routes
            // log records to SlateDB's default tracing formatter on stderr.
            Slatedb.initLogging(LogLevel.INFO, null)
        }
    }

    fun getConnection(
        dbPath: String,
        url: String,
    ): Mono<SlateDbTable> {
        val cacheKey = getCacheKey(dbPath, url)

        return connections.computeIfAbsent(cacheKey) { key ->
            Mono
                .fromFuture {
                    ensureInitialized()
                    ObjectStore.resolve(url).use { objectStore ->
                        DbBuilder(dbPath, objectStore).use { builder ->
                            builder.withMergeOperator(incrementMergeOperator)
                            builder.build()
                        }
                    }
                }
                // ObjectStore.resolve and the synchronous DbBuilder calls touch the
                // foreign runtime; route them off any reactor event loop.
                .subscribeOn(Schedulers.boundedElastic())
                .map { db -> SlateDbTable.create(db) }
                .doOnSuccess { logger.info("Successfully opened SlateDB connection for cacheKey: {}", key) }
                .doOnError { error ->
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
                tableMono.flatMap { table ->
                    table
                        .close()
                        .doOnSuccess { logger.info("Closed SlateDB connection for cacheKey: {}", key) }
                        .doOnError { error -> logger.error("Error closing SlateDB connection for cacheKey: {}", key, error) }
                        .onErrorResume { Mono.empty() }
                }
            }
        return Mono.`when`(closeMonos).doFinally { connections.clear() }
    }
}
