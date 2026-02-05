package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class MemoryStorageBackend : StorageBackend {
    private val store = ByteArrayStore()

    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val bucket = MemoryStorageBucket(store)
        return Mono.just(StorageBuckets(bucket, bucket))
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> = getBucket("", "")

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> = throw UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use getBucket() instead.")

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> = throw UnsupportedOperationException("MemoryStorageBackend does not support HBaseTables. Use getBucket() instead.")

    override fun close() {
        // nothing to close
    }
}
