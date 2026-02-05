package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend
import com.kakao.actionbase.v2.engine.storage.mock.MockStorageBackend

import org.slf4j.LoggerFactory

/**
 * Factory for creating StorageBackend instances.
 *
 * Usage:
 * ```yaml
 * hbase:
 *   type: memory    # memory | embedded | hbase (default)
 * ```
 */
object DefaultStorageBackendFactory {
    private val logger = LoggerFactory.getLogger(DefaultStorageBackendFactory::class.java)
    private lateinit var instance0: StorageBackend

    val INSTANCE: StorageBackend
        get() = instance0

    /**
     * Initializes the storage backend based on the provided properties.
     *
     * @param properties Configuration properties including:
     *   - type: Backend type (memory, embedded, hbase). Defaults to "hbase".
     *   - For HBase type, see HBaseStorageBackend.create for additional properties.
     */
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
                    if (properties.isEmpty() || properties["version"] == "embedded") {
                        logger.info("🚀 - Using Embedded Mock Storage (legacy)")
                        MockStorageBackend()
                    } else {
                        logger.info("Using HBaseStorageBackend")
                        HBaseStorageBackend.create(properties)
                    }
                }
            }
    }

    fun close() {
        if (::instance0.isInitialized) {
            instance0.close()
        }
    }

    /**
     * For testing: reset the factory state.
     */
    internal fun reset() {
        if (::instance0.isInitialized) {
            instance0.close()
        }
    }
}
