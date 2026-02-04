package com.kakao.actionbase.engine.datastore

import com.kakao.actionbase.test.hbase.HBaseTestingCluster
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseConnections

import java.nio.ByteBuffer
import java.nio.ByteOrder

import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.Admin
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.apache.hadoop.hbase.filter.PrefixFilter
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * HBase compatibility test. Default: MockConnection. Set HBASE_MINI_CLUSTER=true for mini cluster.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HBaseDatastoreCompatibilityTest : DatastoreCompatibilityTest() {
    private lateinit var table: Table
    private val tableName = TableName.valueOf("test", "compatibility_test")
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
    }

    @AfterAll
    fun tearDownHBase() {
        table.close()
        if (useMiniCluster) HBaseTestingCluster.stopIfNeeded()
    }

    override fun createStore(): StorageOperations = HBaseOps(table, cf)

    override fun supportsCheckAndMutate() = useMiniCluster

    override fun supportsScanLimit() = useMiniCluster

    override fun cleanup() {
        table.getScanner(Scan()).use { s ->
            s.map { Delete(it.row) }.takeIf { it.isNotEmpty() }?.let { table.delete(it) }
        }
    }

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

    private class HBaseOps(
        private val t: Table,
        private val cf: ByteArray,
    ) : StorageOperations {
        private val q = "v".toByteArray()

        override fun get(key: ByteArray) = t.get(Get(key).addColumn(cf, q)).getValue(cf, q)

        override fun getAll(keys: List<ByteArray>) = t.get(keys.map { Get(it).addColumn(cf, q) }).mapNotNull { r -> r.getValue(cf, q)?.let { r.row to it } }

        override fun scan(
            prefix: ByteArray,
            limit: Int,
        ) = t
            .getScanner(Scan().setFilter(PrefixFilter(prefix)).addColumn(cf, q).setLimit(limit))
            .use { s -> s.mapNotNull { r -> r.getValue(cf, q)?.let { r.row to it } } }

        override fun put(
            key: ByteArray,
            value: ByteArray,
        ) {
            t.put(Put(key).addColumn(cf, q, value))
        }

        override fun delete(key: ByteArray) {
            t.delete(Delete(key))
        }

        override fun increment(
            key: ByteArray,
            delta: Long,
        ) = ByteBuffer
            .wrap(t.increment(Increment(key).addColumn(cf, q, delta)).getValue(cf, q))
            .order(ByteOrder.BIG_ENDIAN)
            .long

        override fun batch(mutations: List<Mutation>) {
            if (mutations.isEmpty()) return
            val actions =
                mutations.map { m ->
                    when (m) {
                        is Mutation.Put -> Put(m.key).addColumn(cf, q, m.value)
                        is Mutation.Delete -> Delete(m.key)
                        is Mutation.Increment -> Increment(m.key).addColumn(cf, q, m.delta)
                    }
                }
            t.batch(actions, arrayOfNulls(actions.size))
        }

        override fun setIfNotExists(
            key: ByteArray,
            value: ByteArray,
        ) = t.checkAndMutate(CheckAndMutate.newBuilder(key).ifNotExists(cf, q).build(Put(key).addColumn(cf, q, value))).isSuccess

        override fun deleteIfEquals(
            key: ByteArray,
            expectedValue: ByteArray,
        ) = t.checkAndMutate(CheckAndMutate.newBuilder(key).ifEquals(cf, q, expectedValue).build(Delete(key))).isSuccess
    }
}
