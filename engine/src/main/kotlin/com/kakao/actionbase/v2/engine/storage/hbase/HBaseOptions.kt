package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.compat.DefaultStorageBackend
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.mock.MockHTable
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import reactor.core.publisher.Mono

/**
 * This supports only HBase 2.4 or below.
 * To support HBase 2.5, use [com.kakao.actionbase.v2.engine.compat.DefaultHBaseCluster]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HBaseOptions(
    val mock: Boolean = false,
    val namespace: String = "",
    val tableName: String = "",
) {
    private val logger = LoggerFactory.getLogger(HBaseOptions::class.java)

    private fun useMockConnection(): Boolean = mock || DefaultStorageBackend.INSTANCE.mock

    // Mock or DefaultHBaseCluster connections is always available.
    fun checkConnection(): Mono<Boolean> = Mono.just(true)

    /**
     * // DefaultHBaseCluster = DHC
     * | mock  | DHC.mock ||| connection    | namespace       | note                               |
     * |-------|----------|||---------------|-----------------|------------------------------------|
     * | true  | -        ||| mock          | given namespace | original logic                     |
     * | -     | true     ||| mock          | given namespace | original logic                     |
     * | false | false    ||| DHC with      | given namespace | use already created DHC connection |
     * |-------|----------|||---------------|-----------------|------------------------------------|
     */
    fun getTables(): Mono<HBaseTables> =
        if (useMockConnection()) {
            logger.info("Using MockHBase for tableName: {}", tableName)
            val conn = HBaseConnections.getMockConnection(namespace)
            val table = NewMockTable(conn.getTable(TableName.valueOf("edges")) as MockHTable)
            val hbaseTable = HBaseTable.create(table)
            Mono.just(HBaseTables(hbaseTable, hbaseTable))
        } else {
            val namespace = if (namespace.isBlank()) DefaultStorageBackend.INSTANCE.namespace else this.namespace
            logger.info("🚀 Using DefaultStorageBackend for tableName: {} (using namespace: {})", tableName, namespace)
            DefaultStorageBackend.INSTANCE.getTable(namespace, tableName).cache()
        }

    companion object {
        fun newConfiguration(): Configuration = Configuration()
    }
}
