package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    fun open(
        namespace: String,
        name: String,
    ): Mono<StorageTable>

    fun open(uri: String): Mono<StorageTable>

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     */
    @Deprecated("Use open() instead", ReplaceWith("open(namespace, name)"))
    fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables>

    /**
     * Returns HBaseTables for backward compatibility with existing Label implementations.
     */
    @Deprecated("Use open() instead", ReplaceWith("open(uri)"))
    fun getTable(uri: String): Mono<HBaseTables>
}
