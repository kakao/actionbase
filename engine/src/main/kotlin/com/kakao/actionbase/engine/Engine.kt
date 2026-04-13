package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.catalog.Catalog
import com.kakao.actionbase.engine.catalog.PeriodicCatalog

import java.time.Duration

class Engine(
    private val catalog: Catalog,
) : AutoCloseable {
    init {
        catalog.bind(this)
    }

    override fun close() {
        catalog.close()
    }

    companion object {
        fun create(
            catalogReloadInitialDelay: Duration = Duration.ZERO,
            catalogReloadInterval: Duration? = null,
        ): Engine {
            val catalog =
                PeriodicCatalog(
                    catalogReloadInitialDelay,
                    catalogReloadInterval,
                )
            return Engine(catalog)
        }
    }
}
