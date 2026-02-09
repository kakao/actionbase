package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.engine.storage.hbase.HBaseStorageTable
import com.kakao.actionbase.test.hbase.HBaseTestingCluster
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseConnections
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable
import com.kakao.actionbase.v2.engine.storage.hbase.impl.HBaseSyncTable
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.Admin
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.apache.hadoop.hbase.client.mock.MockHTable
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

/**
 * HBase compatibility test for StorageTable.
 * Default: MockConnection. Set HBASE_MINI_CLUSTER=true for mini cluster.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HBaseStorageTableCompatibilityTest : StorageTableCompatibilityTest() {
    private lateinit var table: Table
    private lateinit var hbaseTable: HBaseTable
    private val tableName = TableName.valueOf("test", "storage_table_test")
    private val cf = "f".toByteArray()
    private val useMiniCluster = System.getenv("HBASE_MINI_CLUSTER") == "true"

    @BeforeAll
    fun setUpHBase() {
        val connection =
            if (useMiniCluster) {
                HBaseTestingCluster.startIfNeeded()
                HBaseTestingCluster.connection.also { createTableIfNeeded(it.admin) }
            } else {
                HBaseConnections.getMockConnection("test")
            }
        table = connection.getTable(tableName)
        hbaseTable =
            if (useMiniCluster) {
                HBaseSyncTable(table)
            } else {
                HBaseTable.create(NewMockTable(table as MockHTable))
            }
    }

    @AfterAll
    fun tearDownHBase() {
        table.close()
        if (useMiniCluster) HBaseTestingCluster.stopIfNeeded()
    }

    @BeforeEach
    fun cleanup() {
        table.getScanner(Scan()).use { s ->
            s.map { Delete(it.row) }.takeIf { it.isNotEmpty() }?.let { table.delete(it) }
        }
    }

    override fun createTable(): StorageTable = HBaseStorageTable(hbaseTable)

    override fun supportsCheckAndMutate() = useMiniCluster

    override fun supportsScanLimit() = useMiniCluster

    override fun supportsIncrement() = useMiniCluster

    private fun createTableIfNeeded(admin: Admin) {
        val ns = tableName.namespaceAsString
        if (admin.listNamespaceDescriptors().none { it.name == ns }) {
            admin.createNamespace(NamespaceDescriptor.create(ns).build())
        }
        if (!admin.tableExists(tableName)) {
            admin.createTable(
                TableDescriptorBuilder
                    .newBuilder(tableName)
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of(cf))
                    .build(),
            )
        }
    }
}
