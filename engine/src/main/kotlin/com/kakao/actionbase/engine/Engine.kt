package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.catalog.CatalogLoader
import com.kakao.actionbase.engine.catalog.PeriodicCatalogLoader

import java.time.Duration

class Engine(
    private val catalogLoader: CatalogLoader,
) : AutoCloseable {
    init {
        catalogLoader.bind(this)
    }

    override fun close() {
        catalogLoader.close()
    }

    companion object {
        fun create(
            catalogReloadInitialDelay: Duration = Duration.ZERO,
            catalogReloadInterval: Duration? = null,
        ): Engine {
            val catalogLoader =
                PeriodicCatalogLoader(
                    catalogReloadInitialDelay,
                    catalogReloadInterval,
                )
            return Engine(catalogLoader)
        }
    }
}
