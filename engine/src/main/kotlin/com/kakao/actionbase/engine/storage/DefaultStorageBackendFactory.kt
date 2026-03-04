package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.engine.storage.hbase.HBaseStorageBackend
import com.kakao.actionbase.engine.storage.hbase.MockHBaseStorageBackend
import com.kakao.actionbase.engine.storage.memory.MemoryStorageBackend

import org.slf4j.LoggerFactory

/**
 * Factory for creating StorageBackend instances.
 *
 * Thread-safety: This factory is designed to be initialized once at application startup.
 * The initialize() method is synchronized to prevent race conditions during initialization.
 * Once initialized, the factory cannot be re-initialized.
 *
 * Usage:
 * ```yaml
 * hbase:
 *   type: memory    # memory | embedded | hbase (default)
 * ```
 */
object DefaultStorageBackendFactory {
    private val logger = LoggerFactory.getLogger(DefaultStorageBackendFactory::class.java)

    @Volatile
    private var instance0: StorageBackend? = null

    @Volatile
    private var defaultNamespace0: String = "default"

    val INSTANCE: StorageBackend
        get() = instance0 ?: throw IllegalStateException("StorageBackend not initialized. Call initialize() first.")

    val defaultNamespace: String
        get() = defaultNamespace0

    val isInitialized: Boolean
        get() = instance0 != null

    /**
     * Initializes the storage backend based on the provided properties.
     * If already initialized, this method does nothing (idempotent).
     *
     * @param properties Configuration properties including:
     *   - type: Backend type (memory, embedded, hbase). Defaults to "hbase".
     *   - For HBase type, see HBaseStorageBackend.create for additional properties.
     */
    @Synchronized
    fun initialize(properties: Map<String, String>) {
        if (isInitialized) {
            logger.debug("StorageBackend already initialized, skipping")
            return
        }
        val type = properties["type"] ?: "hbase"
        defaultNamespace0 = properties["namespace"] ?: "default"
        logger.info("Initializing StorageBackend with type: {}, namespace: {}", type, defaultNamespace0)

        instance0 =
            when (type) {
                "memory" -> {
                    logger.info("Using MemoryStorageBackend")
                    MemoryStorageBackend()
                }
                "embedded" -> {
                    logger.info("Using MockHBaseStorageBackend (embedded)")
                    MockHBaseStorageBackend()
                }
                else -> {
                    if (properties.isEmpty() || properties["version"] == "embedded") {
                        logger.info("🚀 - Using Embedded Mock Storage (legacy)")
                        MockHBaseStorageBackend()
                    } else {
                        logger.info("Using HBaseStorageBackend")
                        HBaseStorageBackend.create(properties)
                    }
                }
            }
    }

    /**
     * Initializes the factory with a pre-created StorageBackend instance.
     * If already initialized, this method does nothing (idempotent).
     *
     * @param backend The StorageBackend instance to use.
     * @param namespace The default namespace to use.
     */
    @Synchronized
    fun initialize(
        backend: StorageBackend,
        namespace: String = "default",
    ) {
        if (isInitialized) {
            logger.debug("StorageBackend already initialized, skipping")
            return
        }
        logger.info("Initializing StorageBackend with provided instance: {}, namespace: {}", backend::class.simpleName, namespace)
        instance0 = backend
        defaultNamespace0 = namespace
    }

    @Synchronized
    fun close() {
        instance0?.close()
        instance0 = null
    }
}
