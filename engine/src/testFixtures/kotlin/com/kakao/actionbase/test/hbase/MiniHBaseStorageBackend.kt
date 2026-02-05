package com.kakao.actionbase.test.hbase

import com.kakao.actionbase.v2.engine.storage.StorageBackend
import com.kakao.actionbase.v2.engine.storage.StorageBuckets
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBucket
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection

import reactor.core.publisher.Mono

/**
 * Storage backend that uses the HBase mini cluster for testing.
 * This backend creates tables using the provided AsyncConnection.
 */
class MiniHBaseStorageBackend(
    private val connectionMono: Mono<AsyncConnection>,
    private val defaultNamespace: String,
) : StorageBackend {
    override fun getBucket(
        namespace: String,
        name: String,
    ): Mono<StorageBuckets> {
        val effectiveNs = namespace.ifEmpty { defaultNamespace }
        return connectionMono.map { conn ->
            val tableName = TableName.valueOf(effectiveNs, name)
            val asyncTable = conn.getTable(tableName)
            val hbaseTable = HBaseTable.create(asyncTable)
            val bucket = HBaseStorageBucket(hbaseTable)
            StorageBuckets(bucket, bucket)
        }
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
        val effectiveNs = namespace.ifEmpty { defaultNamespace }
        return connectionMono.map { conn ->
            val tableName = TableName.valueOf(effectiveNs, name)
            val asyncTable = conn.getTable(tableName)
            val hbaseTable = HBaseTable.create(asyncTable)
            HBaseTables(hbaseTable, hbaseTable)
        }
    }

    @Deprecated("Use getBucket() instead", ReplaceWith("getBucket(uri)"))
    override fun getTable(uri: String): Mono<HBaseTables> {
        val (ns, name) = parseDatastoreUri(uri)
        return getTable(ns, name)
    }

    override fun close() {
        // Connection is managed by HBaseTestingCluster
    }

    private fun parseDatastoreUri(uri: String): Pair<String, String> {
        val parts = uri.removePrefix("datastore://").split("/")
        require(parts.size == 2) { "Invalid datastore URI: $uri" }
        return parts[0] to parts[1]
    }
}
