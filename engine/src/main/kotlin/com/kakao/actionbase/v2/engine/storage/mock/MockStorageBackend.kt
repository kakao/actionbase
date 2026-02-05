package com.kakao.actionbase.v2.engine.storage.mock

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseConnections
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBucket
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.mock.MockHTable

import reactor.core.publisher.Mono

/**
 * Mock storage backend for testing and embedded mode.
 * Uses HBase MockHTable for storage operations.
 */
class MockStorageBackend : StorageBackend {
    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val conn = HBaseConnections.getMockConnection(namespace)
        val mockTable = conn.getTable(TableName.valueOf("edges")) as MockHTable
        val table = NewMockTable(mockTable)
        val hbaseTable = HBaseTable.create(table)
        val bucket = HBaseStorageBucket(hbaseTable)
        return Mono.just(StorageBuckets(bucket, bucket))
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> {
        val (ns, _) = parseDatastoreUri(uri)
        return getBucket(ns, "")
    }

    override fun close() {
        // nothing to close
    }

    private fun parseDatastoreUri(uri: String): Pair<String, String> {
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri" }
        return parts[0] to parts[1]
    }
}
