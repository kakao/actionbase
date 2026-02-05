package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import org.apache.hadoop.hbase.client.AsyncConnection

import reactor.core.publisher.Mono

class HBaseStorageBackend(
    private val connection: AsyncConnection,
    private val hbaseNamespace: String,
) : StorageBackend {
    override fun buckets(): Mono<StorageBuckets> = Mono.fromCallable { HBaseStorageBuckets(connection.admin, hbaseNamespace) }

    override fun bucket(name: String): Mono<StorageBucket> = Mono.fromCallable { HBaseStorageBucket(connection, hbaseNamespace, name) }
}
