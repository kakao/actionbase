package com.kakao.actionbase.test.hbase

import com.kakao.actionbase.v2.engine.storage.DatastoreUri
import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

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
    override fun open(
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

    override fun open(uri: String): Mono<StorageTable> {
        val (ns, name) = DatastoreUri.parse(uri)
        return open(ns, name)
    }

    @Deprecated("Use open() instead", ReplaceWith("open(namespace, name)"))
    override fun getTable(
        namespace: String,
        name: String,
    ): Mono<HBaseTables> {
        val effectiveNs = namespace.ifEmpty { defaultNamespace }
        return connectionMono.map { conn ->
            val tableName = TableName.valueOf(effectiveNs, name)
            val asyncTable = conn.getTable(tableName)
            val hbaseTable = HBaseTable.create(asyncTable)
            HBaseTables(hbaseTable, hbaseTable)
        }
    }

    @Deprecated("Use open() instead", ReplaceWith("open(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> {
        val (ns, name) = DatastoreUri.parse(uri)
        return getTable(ns, name)
    }

    override fun close() {
        // Connection is managed by HBaseTestingCluster
    }
}
