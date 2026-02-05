package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.mock.MockStorageBackend

import org.apache.hadoop.conf.Configuration
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import reactor.core.publisher.Mono

@JsonIgnoreProperties(ignoreUnknown = true)
data class HBaseOptions(
    val mock: Boolean = false,
    val namespace: String = "",
    val tableName: String = "",
) {
    private val logger = LoggerFactory.getLogger(HBaseOptions::class.java)

    // Mock or default backend connections are always available.
    fun checkConnection(): Mono<Boolean> = Mono.just(true)

    fun getBuckets(): Mono<StorageBuckets> {
        val backend = resolveBackend()
        val resolvedNamespace =
            if (namespace.isBlank()) {
                (backend as? HBaseStorageBackend)?.namespace ?: namespace
            } else {
                namespace
            }

        if (mock) {
            logger.info("Using MockStorageBackend for tableName: {}", tableName)
        } else {
            logger.info("Using StorageBackend for tableName: {} (namespace: {})", tableName, resolvedNamespace)
        }

        return backend
            .getBucket(resolvedNamespace, tableName)
            .cache()
    }

    private fun resolveBackend(): StorageBackend =
        if (mock) {
            MockStorageBackend()
        } else {
            DefaultStorageBackendFactory.INSTANCE
        }

    companion object {
        fun newConfiguration(): Configuration = Configuration()
    }
}
