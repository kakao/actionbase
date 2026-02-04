package com.kakao.actionbase.v2.engine.compat

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.AsyncConnection
import org.slf4j.LoggerFactory

object DefaultStorageBackend {
    private val logger = LoggerFactory.getLogger(DefaultStorageBackend::class.java)

    private lateinit var instance0: StorageBackend

    val INSTANCE: StorageBackend
        get() = instance0

    fun initialize(properties: Map<String, String>) {
        val type = properties["type"]?.lowercase()
        when (type) {
            null,
            "",
            "hbase",
            "embedded",
            "mock",
            -> {
                DefaultHBaseCluster.initialize(properties)
                instance0 = DefaultHBaseCluster.INSTANCE
            }
            else -> {
                logger.warn("Unsupported storage backend type: {}. Falling back to HBase.", type)
                DefaultHBaseCluster.initialize(properties)
                instance0 = DefaultHBaseCluster.INSTANCE
            }
        }
    }

    fun initialize(
        connectionMono: reactor.core.publisher.Mono<AsyncConnection>,
        namespace: String,
        configuration: Configuration,
    ) {
        DefaultHBaseCluster.initialize(connectionMono, namespace, configuration)
        instance0 = DefaultHBaseCluster.INSTANCE
    }
}
