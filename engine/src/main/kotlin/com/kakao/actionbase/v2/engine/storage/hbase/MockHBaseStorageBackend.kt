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
 */
class MockHBaseStorageBackend : StorageBackend {
    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val hbaseTable = createMockHBaseTable(namespace)
        val bucket = HBaseStorageBucket(hbaseTable)
        return Mono.just(StorageBuckets(bucket, bucket))
    }

    override fun getBucket(uri: String): Mono<StorageBuckets> {
        val (ns, _) = parseDatastoreUri(uri)
        return getBucket(ns, "")
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> {
        val hbaseTable = createMockHBaseTable(namespace)
        return Mono.just(HBaseTables(hbaseTable, hbaseTable))
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> {
        val (ns, _) = parseDatastoreUri(uri)
        return getTable(ns, "")
    }

    override fun close() {
        // nothing to close
    }

    /**
     * Creates a mock HBase table using the "edges" table name.
     * All mock tables share the same "edges" table per namespace for backward compatibility.
     */
    private fun createMockHBaseTable(namespace: String): HBaseTable {
        val conn = HBaseConnections.getMockConnection(namespace)
        val mockTable = conn.getTable(TableName.valueOf("edges")) as MockHTable
        val table = NewMockTable(mockTable)
        return HBaseTable.create(table)
    }

    private fun parseDatastoreUri(uri: String): Pair<String, String> {
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri" }
        return parts[0] to parts[1]
    }
}
