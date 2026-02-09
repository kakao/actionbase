package com.kakao.actionbase.engine.storage

import reactor.core.publisher.Mono

interface StorageBackend : AutoCloseable {
    fun getStorageTable(
        namespace: String,
        name: String,
    ): Mono<StorageTable>

    fun getStorageTable(uri: String): Mono<StorageTable> {
        val (ns, name) = DatastoreUri.parse(uri)
        return getStorageTable(ns, name)
    }
}
