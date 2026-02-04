package com.kakao.actionbase.v2.engine.compat

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    val mock: Boolean
    val namespace: String

    fun getTable(
        namespace: String,
        tableName: String,
    ): Mono<HBaseTables>

    fun getTable(uri: String): Mono<HBaseTables>
}
