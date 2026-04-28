package actionbase.core

import actionbase.core.model.HBaseTableSchema
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Connection, Table}

/**
  * Thin [[HBaseService]] adapter over an HBase [[Connection]]. The caller
  * supplies the connection as a `() => Connection` thunk so expensive
  * connection construction can be deferred to the executor (mirroring the
  * original `LazyEval[Connection]` semantics without reintroducing the
  * in-house util package).
  *
  * Replication awareness is advertised through [[isReplicated]]; this impl
  * is non-replicated.
  *
  * @note Slimmed in OSS port: LazyEval replaced with a `() => Connection` thunk.
  */
class HBaseServiceImpl(hBaseConn: () => Connection) extends HBaseService {

  @transient private lazy val connection: Connection = hBaseConn()

  override def createTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Array[Array[Byte]]
  ): Table = {
    val admin = connection.getAdmin
    try {
      val descriptor = hBaseTableSchema.toHBaseTableDescriptor(namespace, tableName)
      admin.createTable(descriptor, regionSplitKeys)
    } finally admin.close()
    getTable(namespace, tableName)
  }

  override def createTable(namespace: String, tableName: String, hBaseTableSchema: HBaseTableSchema): Table = {
    val admin = connection.getAdmin
    try {
      val descriptor = hBaseTableSchema.toHBaseTableDescriptor(namespace, tableName)
      admin.createTable(descriptor)
    } finally admin.close()
    getTable(namespace, tableName)
  }

  override def getTable(namespace: String, tableName: String): Table =
    connection.getTable(TableName.valueOf(namespace, tableName))

  override def recreateTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Seq[Array[Byte]]
  ): Table = {
    val admin = connection.getAdmin
    val table = TableName.valueOf(namespace, tableName)
    try {
      if (admin.tableExists(table)) {
        admin.disableTable(table)
        admin.deleteTable(table)
      }
    } finally admin.close()
    if (regionSplitKeys.isEmpty) {
      createTable(namespace, tableName, hBaseTableSchema)
    } else {
      val recreateAdmin = connection.getAdmin
      try {
        val descriptor = hBaseTableSchema.toHBaseTableDescriptor(namespace, tableName)
        recreateAdmin.createTable(descriptor, regionSplitKeys.toArray[Array[Byte]])
      } finally recreateAdmin.close()
    }
    getTable(namespace, tableName)
  }

  override def bulkLoadHadoopConfiguration: Configuration = connection.getConfiguration

  override def tableSchema: HBaseTableSchema = HBaseTableSchema.default(replication = false)

  override def isReplicated: Boolean = false

  override def exists(namespace: String, tableName: String): Boolean = {
    val admin = connection.getAdmin
    try admin.tableExists(TableName.valueOf(namespace, tableName))
    finally admin.close()
  }
}
