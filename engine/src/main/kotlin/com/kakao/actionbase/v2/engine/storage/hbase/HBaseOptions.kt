package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.engine.storage.HBaseTablesProvider

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
     * Returns HBaseTables for Label implementations that need direct HBase table access.
     */
    fun getTables(): Mono<HBaseTables> {
        val effectiveNs = getEffectiveNamespace()
        logger.debug("Using StorageBackend (HBaseTables) for tableName: {}", tableName)
        val provider =
            DefaultStorageBackendFactory.INSTANCE as? HBaseTablesProvider
                ?: throw IllegalStateException("StorageBackend does not support HBaseTables")
        return provider.getHBaseTables(effectiveNs, tableName).cache()
    }

    companion object {
        fun newConfiguration(): Configuration = Configuration()
    }
}
