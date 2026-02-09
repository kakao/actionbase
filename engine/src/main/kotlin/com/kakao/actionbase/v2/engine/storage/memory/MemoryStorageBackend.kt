package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.DatastoreUri
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import java.util.concurrent.ConcurrentHashMap

import reactor.core.publisher.Mono

class MemoryStorageBackend : StorageBackend {
    private val stores = ConcurrentHashMap<String, ByteArrayStore>()

    private fun getOrCreateStore(
        namespace: String,
        name: String,
    ): ByteArrayStore {
        val key = "$namespace:$name"
        return stores.computeIfAbsent(key) { ByteArrayStore() }
    }

    override fun open(
        namespace: String,
        name: String,
    ): Mono<StorageTable> {
        val store = getOrCreateStore(namespace, name)
        return Mono.just(MemoryStorageTable(store))
    }

    override fun open(uri: String): Mono<StorageTable> {
        val (ns, name) = DatastoreUri.parse(uri)
        return open(ns, name)
    }

    @Deprecated("Use open() instead", ReplaceWith("open(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> = Mono.error(UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use open() instead."))

    @Deprecated("Use open() instead", ReplaceWith("open(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> = Mono.error(UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use open() instead."))

    override fun close() {
        // nothing to close
    }
}
