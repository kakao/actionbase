package com.kakao.actionbase.engine.binding.datastore.hbase

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseAdmin
import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableSchema
import com.kakao.actionbase.test.hbase.HBaseTestingClusterExtension

import java.util.UUID

import org.apache.hadoop.hbase.HConstants
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.util.Bytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import reactor.test.StepVerifier

@ExtendWith(HBaseTestingClusterExtension::class)
class HBaseAdminTest(
    connection: AsyncConnection,
) {
    private val asyncAdmin = connection.admin
    private val admin = HBaseAdmin(Mono.just(connection.admin))
    private var testNamespace: String = makeTestAlphanumericName()
    private val defaultSchema =
        HBaseTableSchema(
            columnFamilyName = "f".toByteArray(),
            numRegions = 1,
        )

    @BeforeEach
    fun setUp() {
        admin.createNamespace(testNamespace).block()
    }

    @Test
    fun shouldCreateNamespace() {
        val namespace = "test_namespace_${System.currentTimeMillis()}"

        admin
            .createNamespace(namespace)
            .test()
            .verifyComplete()
    }

    @Test
    fun shouldCreateAndGetTables() {
        val tableName = makeTestAlphanumericName()

        StepVerifier
            .create(admin.createTable(testNamespace, tableName, defaultSchema))
            .verifyComplete()

        StepVerifier
            .create(admin.getTables(testNamespace))
            .assertNext { tables ->
                assertNotNull(tables.firstOrNull { it.tableName == tableName })
                // Table list may be empty
            }.verifyComplete()
    }

    @Test
    fun shouldGetTable() {
        val tableName = makeTestAlphanumericName()

        createTestTable(tableName)
            .then(admin.getTable(testNamespace, tableName))
            .test()
            .assertNext { hbaseTable ->
                assertNotNull(hbaseTable)
                assertEquals(testNamespace, hbaseTable.namespace)
                assertEquals(tableName, hbaseTable.tableName)
            }.verifyComplete()
    }

    @Test
    fun shouldEnableTable() {
        createTestTable()
            .flatMap { admin.enableTable(testNamespace, it) }
            .test()
            .verifyComplete()
    }

    @Test
    fun shouldDisableTable() {
        createTestTable()
            .flatMap { admin.disableTable(testNamespace, it) }
            .test()
            .verifyComplete()
    }

    @Test
    fun shouldDeleteTable() {
        createTestTable()
            .flatMap { admin.deleteTable(testNamespace, it) }
            .test()
            .verifyComplete()
    }

    @Test
    fun shouldGetTableMetricSummary() {
        createTestTable()
            .flatMap { admin.getTableMetricSummary(testNamespace, it) }
            .test()
            .assertNext { metrics -> assertNotNull(metrics) }
            .verifyComplete()
    }

    @Test
    fun shouldEnableReplication() {
        val tableName = makeTestAlphanumericName()
        createTestTable(tableName)
            .flatMap { admin.enableReplication(testNamespace, it) }
            .then(admin.getTable(testNamespace, tableName))
            .test()
            .assertNext { table -> assertEquals(HConstants.REPLICATION_SCOPE_GLOBAL, table.replicationScope) }
            .verifyComplete()
    }

    @Test
    fun shouldDisableReplication() {
        val tableName = makeTestAlphanumericName()
        createTestTable(tableName)
            .flatMap { admin.enableReplication(testNamespace, it) }
            .then(admin.disableReplication(testNamespace, tableName))
            .then(admin.getTable(testNamespace, tableName))
            .test()
            .assertNext { table -> assertEquals(HConstants.REPLICATION_SCOPE_LOCAL, table.replicationScope) }
            .verifyComplete()
    }

    @Test
    fun shouldFlipReplicationScopeOnAllColumnFamilies() {
        val tableName = makeTestAlphanumericName()
        val table = TableName.valueOf(testNamespace, tableName)
        val extraCf = ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("extra"))

        createTestTable(tableName)
            .then(Mono.defer { Mono.fromCompletionStage(asyncAdmin.addColumnFamily(table, extraCf)) })
            .then(admin.enableReplication(testNamespace, tableName))
            .then(Mono.defer { Mono.fromCompletionStage(asyncAdmin.getDescriptor(table)) })
            .test()
            .assertNext { updated ->
                assertEquals(2, updated.columnFamilies.size, "expected two column families")
                updated.columnFamilies.forEach { cf ->
                    assertEquals(HConstants.REPLICATION_SCOPE_GLOBAL, cf.scope, "CF ${Bytes.toString(cf.name)}")
                }
            }.verifyComplete()
    }

    @Test
    fun shouldBeIdempotentWhenReplicationAlreadyAtTargetScope() {
        val tableName = makeTestAlphanumericName()
        // Default schema creates the table with REPLICATION_SCOPE_LOCAL. Calling disableReplication
        // again must be a no-op (the modify path is skipped) instead of throwing.
        createTestTable(tableName)
            .flatMap { admin.disableReplication(testNamespace, it) }
            .then(admin.getTable(testNamespace, tableName))
            .test()
            .assertNext { table -> assertEquals(HConstants.REPLICATION_SCOPE_LOCAL, table.replicationScope) }
            .verifyComplete()
    }

    private fun createTestTable(tableName: String = makeTestAlphanumericName()): Mono<String> = admin.createTable(testNamespace, tableName, defaultSchema).thenReturn(tableName)

    companion object {
        private fun makeTestAlphanumericName(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
