package com.kakao.actionbase.engine.runtime

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
