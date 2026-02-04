package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.v2.engine.compat.StorageBackend
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import java.util.concurrent.ConcurrentHashMap

import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono

class MemoryStorageBackend private constructor(
    override val namespace: String,
) : StorageBackend {
    private val logger = LoggerFactory.getLogger(MemoryStorageBackend::class.java)

    override val mock: Boolean = false

    private val tables = ConcurrentHashMap<String, MemoryHBaseTable>()

    override fun getTable(
        namespace: String,
        tableName: String,
    ): Mono<HBaseTables> =
        Mono.fromCallable {
            val key = "${namespace.trim()}::$tableName"
            val table =
                tables.computeIfAbsent(key) {
                    logger.info("Using MemoryStorageBackend for tableName: {} (namespace: {})", tableName, namespace)
                    MemoryHBaseTable(namespace, tableName)
                }
            HBaseTables(table, table)
        }

    override fun getTable(uri: String): Mono<HBaseTables> {
        val (namespace, tableName) = parseDatastoreUri(uri)
        return getTable(namespace, tableName)
    }

    private fun parseDatastoreUri(uri: String): Pair<String, String> {
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri. Expected format: datastore://{namespace}/{tableName}" }
        return parts[0] to parts[1]
    }

    override fun close() {
        tables.clear()
    }

    companion object {
        private const val DEFAULT_NAMESPACE = "default"

        fun initialize(properties: Map<String, String>): MemoryStorageBackend {
            val namespace = properties["namespace"] ?: DEFAULT_NAMESPACE
            return MemoryStorageBackend(namespace)
        }
    }
}
