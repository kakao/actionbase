package actionbase.pipeline.testsupport

import actionbase.core.HBaseService
import actionbase.core.model.HBaseTableSchema
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Admin, Connection, Table}

/**
 * Minimal in-test `HBaseService` backed by a live HBase connection (from
 * `HBaseContainer`). Replaces the production `HBaseServiceImpl` for
 * integration tests that otherwise need a cluster-config-aware factory.
 *
 * `isReplicated` is hard-coded `false`; replication/migration variants
 * are not exercised at this level.
 */
class ContainerBackedHBaseService(connection: Connection, hbaseConf: Configuration) extends HBaseService {
  override def bulkLoadHadoopConfiguration: Configuration = hbaseConf
  override def tableSchema: HBaseTableSchema              = HBaseTableSchema.default().copy(compression = "NONE")
  override def isReplicated: Boolean                      = false

  override def exists(namespace: String, tableName: String): Boolean = {
    val admin: Admin = connection.getAdmin
    try admin.tableExists(TableName.valueOf(namespace, tableName))
    finally admin.close()
  }

  override def getTable(namespace: String, tableName: String): Table =
    connection.getTable(TableName.valueOf(namespace, tableName))

  override def createTable(namespace: String, tableName: String, schema: HBaseTableSchema): Table =
    createTable(namespace, tableName, schema, Array.empty[Array[Byte]])

  override def createTable(
      namespace: String,
      tableName: String,
      schema: HBaseTableSchema,
      regionSplitKeys: Array[Array[Byte]]
  ): Table = {
    val admin = connection.getAdmin
    try {
      val descriptor = schema.toHBaseTableDescriptor(namespace, tableName)
      if (regionSplitKeys.isEmpty) admin.createTable(descriptor)
      else admin.createTable(descriptor, regionSplitKeys)
    } finally admin.close()
    getTable(namespace, tableName)
  }

  override def recreateTable(
      namespace: String,
      tableName: String,
      schema: HBaseTableSchema,
      regionSplitKeys: Seq[Array[Byte]]
  ): Table = {
    val admin = connection.getAdmin
    val tn    = TableName.valueOf(namespace, tableName)
    try {
      if (admin.tableExists(tn)) {
        if (admin.isTableEnabled(tn)) admin.disableTable(tn)
        admin.deleteTable(tn)
      }
    } finally admin.close()
    createTable(namespace, tableName, schema, regionSplitKeys.toArray)
  }
}
