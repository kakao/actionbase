package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.DatastoreUri
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
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

    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val store = getOrCreateStore(namespace, name)
        val bucket = MemoryStorageBucket(store)
        return Mono.just(StorageBuckets(bucket, bucket))
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> {
        val (ns, name) = DatastoreUri.parse(uri)
        return getBucket(ns, name)
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> = Mono.error(UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use getBucket() instead."))

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> = Mono.error(UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use getBucket() instead."))

    override fun close() {
        // nothing to close
    }
}
