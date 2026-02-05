package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets>

    fun getBucket(uri: String): Mono<StorageBuckets>

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     */
    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(namespace, name)"))
    fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables>

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     */
    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    fun getTable(uri: String): Mono<HBaseTables>
}
