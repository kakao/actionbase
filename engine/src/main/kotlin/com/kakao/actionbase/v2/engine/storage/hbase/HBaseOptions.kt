package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import org.apache.hadoop.conf.Configuration
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import reactor.core.publisher.Mono

/**
 * HBase storage options for Label configurations.
 * Uses DefaultStorageBackendFactory for storage backend access.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HBaseOptions(
    val mock: Boolean = false,
    val namespace: String = "",
    val tableName: String = "",
) {
    private val logger = LoggerFactory.getLogger(HBaseOptions::class.java)

    // Connection is always available via DefaultStorageBackendFactory.
    fun checkConnection(): Mono<Boolean> = Mono.just(true)

    /**
     * Returns the effective namespace, using DefaultStorageBackendFactory's defaultNamespace as fallback.
     */
    private fun getEffectiveNamespace(): String = namespace.ifEmpty { DefaultStorageBackendFactory.defaultNamespace }

    /**
     * Returns StorageBuckets for the configured namespace and tableName.
     * This is the preferred method for new code.
     */
    fun getBuckets(): Mono<StorageBuckets> {
        val effectiveNs = getEffectiveNamespace()
        logger.info("Using StorageBackend for tableName: {}", tableName)
        return DefaultStorageBackendFactory.INSTANCE.getBucket(effectiveNs, tableName).cache()
    }

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     * @deprecated Use getBuckets() instead
     */
    @Deprecated("Use getBuckets() instead", ReplaceWith("getBuckets()"))
    @Suppress("DEPRECATION")
    fun getTables(): Mono<HBaseTables> {
        val effectiveNs = getEffectiveNamespace()
        logger.info("Using StorageBackend (HBaseTables) for tableName: {}", tableName)
        return DefaultStorageBackendFactory.INSTANCE.getTable(effectiveNs, tableName).cache()
    }

    companion object {
        fun newConfiguration(): Configuration = Configuration()
    }
}
