package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.test.hbase.HBaseTestingClusterConfig
import com.kakao.actionbase.test.hbase.HBaseTestingClusterExtension
import com.kakao.actionbase.v2.engine.storage.Storage

import org.apache.hadoop.hbase.TableName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import reactor.test.StepVerifier

@ExtendWith(HBaseTestingClusterExtension::class)
class HBaseOptionsTest(
    private val config: HBaseTestingClusterConfig,
) {
    @Test
    fun `ignore zkHosts`() =
        test(config.tableName) { tableName ->
            val legacyConf =
                makeConfig(tableName.namespaceAsString, tableName.qualifierAsString) {
                    put("zkHosts", "actionbase-hm1.example.com:2181,actionbase-hm2.example.com:2181")
                    put("useLockTable", false)
                }

            Storage
                .parseOptions<HBaseOptions>(legacyConf)
        }

    @Test
    fun `ignore option test`() =
        test(config.tableName) { tableName ->

            val legacyConf =
                makeConfig(tableName.namespaceAsString, tableName.qualifierAsString) {
                    put("tablePrefix", "prefix")
                    put("tableSuffix", "suffix")
                    put("zkHosts", "actionbase-hm1.example.com:2181")
                    put("useLockTable", true)
                }

            Storage
                .parseOptions<HBaseOptions>(legacyConf)
        }

    @Test
    fun `use option namespace`() =
        test(config.tableName) { tableName ->

            Storage
                .parseOptions<HBaseOptions>(makeConfig(tableName.namespaceAsString, tableName.qualifierAsString))
        }

    @Test
    fun `use default hbase cluster namespace`() =
        test(config.tableName) { tableName ->

            Storage
                .parseOptions<HBaseOptions>(makeConfig("", tableName.qualifierAsString))
        }

    private fun makeConfig(
        namespace: String,
        tableName: String,
        others: ObjectNode.() -> ObjectNode = { this },
    ): ObjectNode =
        objectMapper
            .createObjectNode()
            .apply {
                put("namespace", namespace)
                put("tableName", tableName)
            }.let { others(it) }

    private fun test(
        expectTableName: TableName,
        optionProvider: (TableName) -> HBaseOptions,
    ) {
        val options = optionProvider(expectTableName)
        StepVerifier
            .create(options.getTables())
            .assertNext { tables ->
                assertNotNull(tables)
                assertNotNull(tables.edge)
                assertNotNull(tables.lock)
                assertEquals(expectTableName, tables.edge.name)
            }.verifyComplete()
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
