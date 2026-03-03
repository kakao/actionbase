package com.kakao.actionbase.test.hbase

import com.kakao.actionbase.v2.engine.compat.DefaultHBaseCluster

import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Connection
import org.apache.hadoop.hbase.client.Table
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

import reactor.core.publisher.Mono

class HBaseTestingClusterExtension :
    BeforeAllCallback,
    ParameterResolver {
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                HBaseTestingCluster.stopIfNeeded()
            },
        )
    }

    override fun beforeAll(context: ExtensionContext) {
        HBaseTestingCluster.startIfNeeded()
        DefaultHBaseCluster.initialize(Mono.just(HBaseTestingCluster.asyncConnection), "ab_test", HBaseTestingCluster.hbaseConfiguration)
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean =
        parameterContext.parameter.type == Connection::class.java ||
            parameterContext.parameter.type == Table::class.java ||
            parameterContext.parameter.type == AsyncConnection::class.java ||
            parameterContext.parameter.type == AsyncTable::class.java ||
            parameterContext.parameter.type == HBaseTestingClusterConfig::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any =
        when (parameterContext.parameter.type) {
            Connection::class.java ->
                HBaseTestingCluster.connection
            Table::class.java -> {
                HBaseTestingCluster.connection.getTable(
                    HBaseTestingCluster.config.tableName,
                )
            }
            AsyncConnection::class.java ->
                HBaseTestingCluster.asyncConnection
            AsyncTable::class.java ->
                HBaseTestingCluster.asyncConnection.getTable(
                    HBaseTestingCluster.config.tableName,
                )
            HBaseTestingClusterConfig::class.java ->
                HBaseTestingCluster.config
            else -> throw IllegalArgumentException("Unsupported parameter type: ${parameterContext.parameter.type}")
        }
}
