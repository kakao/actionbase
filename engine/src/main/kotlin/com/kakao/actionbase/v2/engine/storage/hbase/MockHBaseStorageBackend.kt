package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.DatastoreUri
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageTable
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
    override fun open(
        namespace: String,
        name: String,
    ): Mono<StorageTable> {
        val hbaseTable = createMockHBaseTable(namespace, name)
        return Mono.just(HBaseStorageTable(hbaseTable))
    }

    override fun open(uri: String): Mono<StorageTable> {
        val (ns, name) = DatastoreUri.parse(uri)
        return open(ns, name)
    }

    @Deprecated("Use open() instead", ReplaceWith("open(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> {
        val hbaseTable = createMockHBaseTable(namespace, name)
        return Mono.just(HBaseTables(hbaseTable, hbaseTable))
    }

    @Deprecated("Use open() instead", ReplaceWith("open(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> {
        val (ns, name) = DatastoreUri.parse(uri)
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
}
