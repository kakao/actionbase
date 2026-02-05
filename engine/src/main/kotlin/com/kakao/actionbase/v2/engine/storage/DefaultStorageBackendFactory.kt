package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend
import com.kakao.actionbase.v2.engine.storage.hbase.MockHBaseStorageBackend
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend

import org.slf4j.LoggerFactory

/**
 * Factory for creating StorageBackend instances.
 *
 * Thread-safety: This factory is designed to be initialized once at application startup.
 * The initialize() method is synchronized to prevent race conditions during initialization.
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
     *
     * @param properties Configuration properties including:
     *   - type: Backend type (memory, embedded, hbase). Defaults to "hbase".
     *   - For HBase type, see HBaseStorageBackend.create for additional properties.
     * @throws IllegalStateException if already initialized (call reset() first for re-initialization)
     */
    @Synchronized
    fun initialize(properties: Map<String, String>) {
        check(!isInitialized) {
            "StorageBackend already initialized. Call reset() before re-initializing."
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
     * This is primarily used for testing with embedded HBase clusters.
     *
     * @param backend The StorageBackend instance to use.
     * @param namespace The default namespace to use.
     * @throws IllegalStateException if already initialized (call reset() first for re-initialization)
     */
    @Synchronized
    fun initialize(
        backend: StorageBackend,
        namespace: String = "default",
    ) {
        check(!isInitialized) {
            "StorageBackend already initialized. Call reset() before re-initializing."
        }
        logger.info("Initializing StorageBackend with provided instance: {}, namespace: {}", backend::class.simpleName, namespace)
        instance0 = backend
        defaultNamespace0 = namespace
    }

    fun close() {
        instance0?.close()
    }

    /**
     * For testing: reset the factory state to allow re-initialization.
     */
    @Synchronized
    internal fun reset() {
        instance0?.close()
        instance0 = null
        defaultNamespace0 = "default"
    }
}
