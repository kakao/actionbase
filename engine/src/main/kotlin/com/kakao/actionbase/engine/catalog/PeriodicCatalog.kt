package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.DatabaseId
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.TableId
import com.kakao.actionbase.engine.Engine

import java.time.Duration
import java.time.Instant

import org.slf4j.LoggerFactory

import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class PeriodicCatalog(
    private val catalogReloadInitialDelay: Duration,
    private val catalogReloadInterval: Duration?,
) : Catalog {
    // --- state ---
    @Volatile private var snapshot: Snapshot = Snapshot.EMPTY

    @Volatile private var reloadCount: Long = 0

    @Volatile private var lastReloadAt: Instant? = null

    @Volatile private var engine: Engine? = null

    private var bound = false

    private var disposable: Disposable? = null

    // --- public views ---
    override val databases: Map<DatabaseId, DatabaseDescriptor> get() = snapshot.databases
    override val tables: Map<TableId, TableDescriptor<*>> get() = snapshot.tables
    override val aliases: Map<TableId, AliasDescriptor> get() = snapshot.aliases

    @Synchronized
    override fun bind(engine: Engine) {
        if (bound) {
            log.warn("PeriodicCatalog already bound")
            return
        }
        this.engine = engine
        bound = true

        val interval = catalogReloadInterval
        if (interval == null) {
            log.info(
                "catalog periodic reload disabled; one-shot reload after {} ms.",
                catalogReloadInitialDelay.toMillis(),
            )
        } else {
            log.info(
                "Starting Flux.interval for reloading catalog every {} ms after {} ms delay.",
                interval.toMillis(),
                catalogReloadInitialDelay.toMillis(),
            )
        }

        val source: Flux<Long> =
            if (interval == null) {
                Mono.delay(catalogReloadInitialDelay).flux()
            } else {
                Flux.interval(catalogReloadInitialDelay, interval)
            }

        disposable =
            source
                .onBackpressureDrop { log.warn("backpressure drop {}", it) }
                .doOnNext { reload() }
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorContinue { error, _ ->
                    log.error(
                        "Error occurred during catalog reload or unexpected error: {}. Continuing with next interval.",
                        error.message,
                        error,
                    )
                }.subscribe()
    }

    @Synchronized
    override fun close() {
        if (!bound) return
        log.info("PeriodicCatalog closing after {} reloads", reloadCount)
        disposable?.dispose()
        disposable = null
        engine = null
    }

    fun reloadCount(): Long = reloadCount

    fun lastReloadAt(): Instant? = lastReloadAt

    private fun reload() {
        // Guards against an in-flight tick that fires after `close()`
        // nulled out `engine`. `engine` is @Volatile so this read has a
        // happens-before with close()'s write. Phase 2 will read the
        // catalog through `engine` here and swap `snapshot` atomically.
        if (engine == null) return
        log.debug("reloading catalog")
        // Phase 2: snapshot = Snapshot(loadedDbs, loadedTables, loadedAliases)
        reloadCount++
        lastReloadAt = Instant.now()
    }

    private data class Snapshot(
        val databases: Map<DatabaseId, DatabaseDescriptor>,
        val tables: Map<TableId, TableDescriptor<*>>,
        val aliases: Map<TableId, AliasDescriptor>,
    ) {
        companion object {
            val EMPTY = Snapshot(emptyMap(), emptyMap(), emptyMap())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PeriodicCatalog::class.java)
    }
}
