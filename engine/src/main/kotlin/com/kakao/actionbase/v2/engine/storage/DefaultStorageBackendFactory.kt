package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend
import com.kakao.actionbase.v2.engine.storage.mock.MockStorageBackend

import org.slf4j.LoggerFactory

object DefaultStorageBackendFactory {
    private val logger = LoggerFactory.getLogger(DefaultStorageBackendFactory::class.java)
    private lateinit var instance0: StorageBackend

    val INSTANCE: StorageBackend
        get() = instance0

    fun initialize(properties: Map<String, String>) {
        val type = properties["type"] ?: "hbase"
        logger.info("Initializing StorageBackend with type: {}", type)

        instance0 =
            when (type) {
                "memory" -> {
                    logger.info("Using MemoryStorageBackend")
                    MemoryStorageBackend()
                }
                "embedded" -> {
                    logger.info("Using MockStorageBackend (embedded)")
                    MockStorageBackend()
                }
                else -> {
                    logger.info("Using HBaseStorageBackend")
                    HBaseStorageBackend.create(properties)
                }
            }
    }

    fun initialize(backend: StorageBackend) {
        instance0 = backend
    }

    fun close() {
        if (::instance0.isInitialized) {
            instance0.close()
        }
    }
}
