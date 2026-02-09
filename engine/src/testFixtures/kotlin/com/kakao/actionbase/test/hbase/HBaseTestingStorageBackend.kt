package com.kakao.actionbase.test.hbase

import com.kakao.actionbase.engine.storage.StorageBackend
import com.kakao.actionbase.engine.storage.StorageTable
import com.kakao.actionbase.engine.storage.hbase.HBaseStorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection

import reactor.core.publisher.Mono

/**
 * Storage backend that uses the HBase testing cluster.
 * This backend creates tables using the provided AsyncConnection.
 */
class HBaseTestingStorageBackend(
    private val connectionMono: Mono<AsyncConnection>,
    private val defaultNamespace: String,
) : StorageBackend {
    override fun getStorageTable(
        namespace: String,
        name: String,
    ): Mono<StorageTable> {
        val effectiveNs = namespace.ifEmpty { defaultNamespace }
        return connectionMono.map { conn ->
            val tableName = TableName.valueOf(effectiveNs, name)
            val asyncTable = conn.getTable(tableName)
            val hbaseTable = HBaseTable.create(asyncTable)
            HBaseStorageTable(hbaseTable)
        }
    }

    override fun close() {
        // Connection is managed by HBaseTestingCluster
    }
}
