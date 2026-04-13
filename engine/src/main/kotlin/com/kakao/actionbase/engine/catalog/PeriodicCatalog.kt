package com.kakao.actionbase.engine.catalog

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
    @Volatile private var reloadCount: Long = 0

    @Volatile private var lastReloadAt: Instant? = null
    private var engine: Engine? = null
    private var bound = false
    private var disposable: Disposable? = null

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
        // nulled out `engine`. Phase 2 will read the catalog through
        // `engine` here, which makes this null check load-bearing.
        if (engine == null) return
        log.debug("reloading catalog")
        reloadCount++
        lastReloadAt = Instant.now()
    }

    companion object {
        private val log = LoggerFactory.getLogger(PeriodicCatalog::class.java)
    }
}
