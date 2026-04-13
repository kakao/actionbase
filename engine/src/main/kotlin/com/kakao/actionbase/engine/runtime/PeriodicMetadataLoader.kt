package com.kakao.actionbase.engine.runtime

import java.time.Duration
import java.time.Instant

import org.slf4j.LoggerFactory

import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Pull-based [MetadataLoader] that periodically reloads the metastore.
 *
 * `bind` schedules an initial reload to fire after `metastoreReloadInitialDelay`.
 * If a periodic interval is configured, that initial reload is the first
 * tick of `Flux.interval(initialDelay, interval)` and the loop continues
 * every interval afterwards. If the interval is `null`, only the one-shot
 * initial reload runs.
 *
 * Phase 1 (#247) is a bare loop — [reload] does no real loading yet.
 * Phase 2 will fill it in by reading the metadata labels through the
 * bound [Engine] (Actionbase metadata is itself stored as Actionbase
 * data).
 *
 * Mirrors `Graph.startMetastoreReload` so the v2 god-class can be replaced
 * one consumer at a time.
 *
 * `close` cancels the subscription but does not await an in-flight reload
 * already inside `doOnNext`. Phase 1 reloads are pure counter bumps so
 * the race is harmless; phase 2 will need to revisit this once `reload`
 * does real I/O.
 */
class PeriodicMetadataLoader(
    private val metastoreReloadInitialDelay: Duration,
    private val metastoreReloadInterval: Duration?,
) : MetadataLoader {
    @Volatile private var reloadCount: Long = 0
    @Volatile private var lastReloadAt: Instant? = null
    private var engine: Engine? = null
    private var bound = false
    private var disposable: Disposable? = null

    @Synchronized
    override fun bind(engine: Engine) {
        if (bound) {
            log.warn("PeriodicMetadataLoader already bound")
            return
        }
        this.engine = engine
        bound = true

        val interval = metastoreReloadInterval
        if (interval == null) {
            log.info(
                "metastore periodic reload disabled; one-shot reload after {} ms.",
                metastoreReloadInitialDelay.toMillis(),
            )
        } else {
            log.info(
                "Starting Flux.interval for reloading metastore every {} ms after {} ms delay.",
                interval.toMillis(),
                metastoreReloadInitialDelay.toMillis(),
            )
        }

        val source: Flux<Long> = if (interval == null) {
            Mono.delay(metastoreReloadInitialDelay).flux()
        } else {
            Flux.interval(metastoreReloadInitialDelay, interval)
        }

        disposable = source
            .onBackpressureDrop { log.warn("backpressure drop {}", it) }
            .doOnNext { reload() }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorContinue { error, _ ->
                log.error(
                    "Error occurred during metastore reload or unexpected error: {}. Continuing with next interval.",
                    error.message,
                    error,
                )
            }
            .subscribe()
    }

    @Synchronized
    override fun close() {
        if (!bound) return
        log.info("PeriodicMetadataLoader closing after {} reloads", reloadCount)
        disposable?.dispose()
        disposable = null
        engine = null
    }

    fun reloadCount(): Long = reloadCount

    fun lastReloadAt(): Instant? = lastReloadAt

    private fun reload() {
        // Guards against an in-flight tick that fires after `close()`
        // nulled out `engine`. Phase 2 will read metadata through `engine`
        // here, which makes this null check load-bearing.
        if (engine == null) return
        log.debug("reloading metastore")
        reloadCount++
        lastReloadAt = Instant.now()
    }

    companion object {
        private val log = LoggerFactory.getLogger(PeriodicMetadataLoader::class.java)
    }
}
