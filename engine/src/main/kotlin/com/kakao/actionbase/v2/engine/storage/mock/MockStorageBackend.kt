package com.kakao.actionbase.v2.engine.storage.mock

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend

import reactor.core.publisher.Mono

class MockStorageBackend : StorageBackend {
    private val backend = HBaseStorageBackend.create(mapOf("version" to "embedded"))

    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> = backend.getBucket(namespace, name)

    override fun getBucket(uri: String): Mono<StorageBuckets> = backend.getBucket(uri)

    override fun close() {
        backend.close()
    }
}
