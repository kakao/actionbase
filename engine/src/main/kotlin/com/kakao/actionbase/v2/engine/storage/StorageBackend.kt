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
     * This method will be deprecated once all Labels migrate to use StorageBuckets.
     *
     * @deprecated Use getBucket() instead
     */
    fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables>

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     * This method will be deprecated once all Labels migrate to use StorageBuckets.
     *
     * @deprecated Use getBucket() instead
     */
    fun getTable(uri: String): Mono<HBaseTables>
}
