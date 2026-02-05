package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.mock.MockHTable

import reactor.core.publisher.Mono

/**
 * Mock HBase storage backend for testing and embedded mode.
 * Uses HBase MockHTable for storage operations.
 *
 * Each namespace + name combination gets its own isolated table.
 */
class MockHBaseStorageBackend : StorageBackend {
    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val hbaseTable = createMockHBaseTable(namespace, name)
        val bucket = HBaseStorageBucket(hbaseTable)
        return Mono.just(StorageBuckets(bucket, bucket))
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> {
        val (ns, name) = parseDatastoreUri(uri)
        return getBucket(ns, name)
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> {
        val hbaseTable = createMockHBaseTable(namespace, name)
        return Mono.just(HBaseTables(hbaseTable, hbaseTable))
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> {
        val (ns, name) = parseDatastoreUri(uri)
        return getTable(ns, name)
    }

    override fun close() {
        // nothing to close
    }

    /**
     * Creates a mock HBase table with proper namespace:name isolation.
     */
    private fun createMockHBaseTable(
        namespace: String,
        name: String,
    ): HBaseTable {
        val conn = HBaseConnections.getMockConnection(namespace)
        val tableName = if (name.isEmpty()) "edges" else name
        val mockTable = conn.getTable(TableName.valueOf(tableName)) as MockHTable
        val table = NewMockTable(mockTable)
        return HBaseTable.create(table)
    }

    private fun parseDatastoreUri(uri: String): Pair<String, String> {
        require(uri.startsWith("datastore://")) { "Invalid datastore URI: $uri. Must start with 'datastore://'" }
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri. Expected format: datastore://{namespace}/{tableName}" }
        return parts[0] to parts[1]
    }
}
