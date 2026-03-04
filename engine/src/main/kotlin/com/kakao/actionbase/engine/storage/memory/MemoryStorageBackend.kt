package com.kakao.actionbase.engine.storage.memory

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.engine.storage.StorageBackend
import com.kakao.actionbase.engine.storage.StorageTable

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

    override fun getStorageTable(
        namespace: String,
        name: String,
    ): Mono<StorageTable> {
        val store = getOrCreateStore(namespace, name)
        return Mono.just(MemoryStorageTable(store))
    }

    override fun close() {
        // nothing to close
    }
}
