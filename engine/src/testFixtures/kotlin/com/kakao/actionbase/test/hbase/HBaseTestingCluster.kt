package com.kakao.actionbase.test.hbase

import java.util.concurrent.atomic.AtomicReference

import org.apache.hadoop.hbase.HBaseTestingUtility
import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.Connection
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.apache.hadoop.hbase.util.Bytes
import org.slf4j.LoggerFactory

object HBaseTestingCluster {
    private val logger = LoggerFactory.getLogger(HBaseTestingCluster::class.java)

    private val connectionReference: AtomicReference<Connection?> = AtomicReference(null)
    private val asyncConnectionReference: AtomicReference<AsyncConnection?> = AtomicReference(null)
    private val utilReference: AtomicReference<HBaseTestingUtility?> = AtomicReference(null)

    @Volatile private var started = false
    private val lock = Any()

    val config: HBaseTestingClusterConfig =
        HBaseTestingClusterConfig(
            tableName = TableName.valueOf("ab_test:test_table"),
            columnFamily = Bytes.toBytes("f"),
        )

    val connection: Connection
        get() {
            ensureStarted()
            return connectionReference.updateAndGet { existing ->
                existing ?: utilReference.get()?.connection
                    ?: throw IllegalStateException("HBase cluster not properly initialized")
            }!!
        }

    val asyncConnection: AsyncConnection
        get() {
            ensureStarted()
            return asyncConnectionReference.updateAndGet { existing ->
                existing ?: try {
                    val util =
                        utilReference.get()
                            ?: throw IllegalStateException("HBase cluster not properly initialized")
                    ConnectionFactory.createAsyncConnection(util.configuration).get()
                } catch (e: Exception) {
                    logger.error("Failed to create async connection", e)
                    throw IllegalStateException("Failed to create async connection", e)
                }
            }!!
        }

    val hbaseConfiguration: org.apache.hadoop.conf.Configuration
        get() {
            ensureStarted()
            return utilReference.get()?.configuration
                ?: throw IllegalStateException("HBase cluster not properly initialized")
        }

    private fun ensureStarted() {
        if (!started) {
            throw IllegalStateException("HBase cluster is not started. Call startIfNeeded() first.")
        }
    }

    fun startIfNeeded() {
        if (started) return

        synchronized(lock) {
            if (started) return

            try {
                val util = HBaseTestingUtility()
                configureHBase(util)

                logger.info("Starting HBase mini cluster...")
                util.startMiniCluster()

                utilReference.set(util)

                started = true

                createTestTable()
                logger.info("HBase mini cluster started successfully")
            } catch (e: Exception) {
                logger.error("Failed to start HBase mini cluster", e)
                cleanup()
                throw RuntimeException("Failed to start HBase mini cluster", e)
            }
        }
    }

    private fun configureHBase(util: HBaseTestingUtility) {
        val conf = util.configuration

        // Force loopback so the mini cluster does not depend on the host's hostname
        // resolving to a currently-bindable interface (laptops change networks).
        conf.set("hbase.master.ipc.address", "127.0.0.1")
        conf.set("hbase.regionserver.ipc.address", "127.0.0.1")
        conf.set("hbase.master.hostname", "localhost")
        conf.set("hbase.regionserver.hostname", "localhost")
        conf.set("hbase.localcluster.assign.random.ports", "true")

        // Minimal/core tuning only (focus on significant ones)
        conf.setInt("hbase.master.info.port", -1)
        conf.setInt("hbase.regionserver.info.port", -1)
        conf.setBoolean("hbase.replication", false)
        conf.setBoolean("hbase.security.authorization", false)
        conf.set("hbase.security.authentication", "simple")
        conf.setBoolean("dfs.permissions", false)
        conf.setInt("dfs.replication", 1)
        conf.setInt("hbase.balancer.period", Int.MAX_VALUE)
        conf.setLong("hbase.hregion.majorcompaction", 0L)
        conf.setInt("hbase.master.cleaner.interval", Int.MAX_VALUE)
        conf.setInt("hbase.regionserver.optionalcacheflushinterval", 0)

        // Allow testing without LZ4 native library
        conf.setBoolean("hbase.table.sanity.checks", false)
    }

    private fun createTestTable() {
        val nd = NamespaceDescriptor.create(config.tableName.namespaceAsString).build()

        val td =
            TableDescriptorBuilder
                .newBuilder(config.tableName)
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of(config.columnFamily))
                .build()

        connection.admin.use { admin ->
            admin.createNamespace(nd)
            logger.info("Created test namespace: ${nd.name}")

            admin.createTable(td)
            logger.info("Created test table: ${td.tableName}")
        }
    }

    fun stopIfNeeded() {
        if (!started) return

        synchronized(lock) {
            if (!started) return

            logger.info("Stopping HBase mini cluster...")
            cleanup()
            started = false
            logger.info("HBase mini cluster stopped")
        }
    }

    private fun cleanup() {
        // Clean up connections
        try {
            connectionReference.getAndSet(null)?.close()
        } catch (e: Exception) {
            logger.warn("Error closing connection", e)
        }

        try {
            asyncConnectionReference.getAndSet(null)?.close()
        } catch (e: Exception) {
            logger.warn("Error closing async connection", e)
        }

        // Clean up util
        try {
            utilReference.getAndSet(null)?.shutdownMiniCluster()
        } catch (e: Exception) {
            logger.warn("Error shutting down mini cluster", e)
        }
    }
}
