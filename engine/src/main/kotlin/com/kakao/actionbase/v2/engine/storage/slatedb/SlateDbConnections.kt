package com.kakao.actionbase.v2.engine.storage.slatedb

import com.kakao.actionbase.v2.engine.util.getLogger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import io.slatedb.SlateDb
import io.slatedb.SlateDbConfig
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

// The SlateDB C library uses a single global Tokio runtime that does not support
// concurrent block_on calls from multiple threads. All native FFI calls are routed
// through this global single-thread scheduler to prevent concurrent runtime entries.
//
// Uses Schedulers.fromExecutorService rather than Schedulers.newSingle to avoid
// marking the worker thread as Reactor NonBlocking. NonBlocking threads are
// monitored by BlockHound, which conflicts with the intentional blocking FFI calls.
object SlateDbScheduler {
    val INSTANCE: reactor.core.scheduler.Scheduler =
        Schedulers.fromExecutorService(
            Executors.newSingleThreadExecutor { r -> Thread(r, "slatedb-worker") },
            "slatedb-worker",
        )
}

object SlateDbConnections {
    private val logger = getLogger()

    private val initialized = AtomicBoolean(false)
    private val connections: ConcurrentHashMap<String, Mono<SlateDbTable>> = ConcurrentHashMap()

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            logger.info("Initializing SlateDB (native library loaded from JAR classpath)")
            SlateDb.initLogging(SlateDbConfig.LogLevel.INFO)
        }
    }

    fun getConnection(
        dbPath: String,
        url: String,
    ): Mono<SlateDbTable> {
        val cacheKey = getCacheKey(dbPath, url)

        return connections.computeIfAbsent(cacheKey) { key ->
            Mono
                .fromCallable {
                    ensureInitialized()
                    val db =
                        SlateDb.builder(dbPath, url, null).use { builder ->
                            builder.withMergeOperator(incrementMergeOperator)
                            builder.build()
                        }
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
                            // Close on the same single-thread scheduler so it is enqueued
                            // after all pending FFI operations, preventing use-after-close.
                            }.subscribeOn(SlateDbScheduler.INSTANCE)
                    }
            }
        return Mono.`when`(closeMonos).doFinally { connections.clear() }
    }
}
