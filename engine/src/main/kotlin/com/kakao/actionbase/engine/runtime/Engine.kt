package com.kakao.actionbase.engine.runtime

import java.time.Duration

class Engine(
    private val metadataLoader: MetadataLoader,
) : AutoCloseable {
    init {
        metadataLoader.bind(this)
    }

    override fun close() {
        metadataLoader.close()
    }

    companion object {
        fun create(
            metastoreReloadInitialDelay: Duration = Duration.ZERO,
            metastoreReloadInterval: Duration? = null,
        ): Engine {
            val metadataLoader = PeriodicMetadataLoader(
                metastoreReloadInitialDelay,
                metastoreReloadInterval,
            )
            return Engine(metadataLoader)
        }
    }
}
