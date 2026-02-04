package com.kakao.actionbase.v2.engine.compat

import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend

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
            "memory" -> {
                instance0 = MemoryStorageBackend.initialize(properties)
            }
            else -> {
                logger.warn("Unsupported storage backend type: {}. Falling back to HBase.", type)
                DefaultHBaseCluster.initialize(properties)
                instance0 = DefaultHBaseCluster.INSTANCE
            }
        }
    }
}
