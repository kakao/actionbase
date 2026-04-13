package com.kakao.actionbase.engine.runtime

import java.time.Duration

/**
 * V3-native engine. Composition root and lifecycle handle for the runtime.
 * See #247.
 */
class Engine(
    private val loader: MetadataLoader,
) : AutoCloseable {
    init {
        loader.bind(this)
    }

    override fun close() {
        loader.close()
    }

    companion object {
        fun create(
            metastoreReloadInitialDelay: Duration = Duration.ZERO,
            metastoreReloadInterval: Duration? = null,
        ): Engine = Engine(
            PeriodicMetadataLoader(
                metastoreReloadInitialDelay,
                metastoreReloadInterval,
            ),
        )
    }
}
