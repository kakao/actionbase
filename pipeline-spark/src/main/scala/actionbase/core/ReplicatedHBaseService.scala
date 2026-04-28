package actionbase.core

import actionbase.core.model.HBaseTableSchema
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.Table

/**
  * Marker trait for replication-aware HBase services. The OSS pipeline no
  * longer dispatches via `isInstanceOf[ReplicatedHBaseService]` (replaced
  * by [[HBaseService.isReplicated]] in slim-3) but the trait is retained
  * so driver callers can keep the replication-aware schema default.
  */
trait ReplicatedHBaseService extends HBaseService {
  override def tableSchema: HBaseTableSchema = HBaseTableSchema.default(replication = true)
  override def isReplicated: Boolean         = true
}

/**
  * Dual-write [[HBaseService]]. Writes go to both primary and secondary;
  * reads (getTable / exists) are served from the primary. Bulk-load
  * Hadoop configuration follows the primary (the HFile source location).
  */
class ReplicatedHBaseServiceImpl(
    val primary: HBaseService,
    val secondary: HBaseService
) extends ReplicatedHBaseService {

  override def createTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Array[Array[Byte]]
  ): Table = {
    primary.createTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    secondary.createTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
  }

  override def createTable(namespace: String, tableName: String, hBaseTableSchema: HBaseTableSchema): Table = {
    primary.createTable(namespace, tableName, hBaseTableSchema)
    secondary.createTable(namespace, tableName, hBaseTableSchema)
  }

  override def getTable(namespace: String, tableName: String): Table =
    primary.getTable(namespace, tableName)

  override def recreateTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Seq[Array[Byte]]
  ): Table = {
    primary.recreateTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    secondary.recreateTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    getTable(namespace, tableName)
  }

  override def bulkLoadHadoopConfiguration: Configuration = primary.bulkLoadHadoopConfiguration

  override def exists(namespace: String, tableName: String): Boolean = primary.exists(namespace, tableName)
}

/**
  * Migration-aware dual-write service used during a before/after cluster
  * transition. Writes hit the `after` pair first and then the `before`
  * cluster so replication is always seeded from the new primary.
  *
  * Replication topology:
  * {{{
  *   before <-> after(newPrimary) <-> after(newSecondary)
  * }}}
  * Bulk-load HDFS root stays with `before` because the legacy cluster
  * still serves as the HFile source during migration.
  */
class MigrationHBaseServiceImpl(
    private val before: HBaseService,
    private val after: ReplicatedHBaseServiceImpl
) extends ReplicatedHBaseService {

  override def createTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Array[Array[Byte]]
  ): Table = {
    after.createTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    before.createTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
  }

  override def createTable(namespace: String, tableName: String, hBaseTableSchema: HBaseTableSchema): Table = {
    after.createTable(namespace, tableName, hBaseTableSchema)
    before.createTable(namespace, tableName, hBaseTableSchema)
  }

  override def getTable(namespace: String, tableName: String): Table =
    after.getTable(namespace, tableName)

  override def recreateTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Seq[Array[Byte]]
  ): Table = {
    after.recreateTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    before.recreateTable(namespace, tableName, hBaseTableSchema, regionSplitKeys)
    getTable(namespace, tableName)
  }

  override def bulkLoadHadoopConfiguration: Configuration = before.bulkLoadHadoopConfiguration

  override def exists(namespace: String, tableName: String): Boolean = before.exists(namespace, tableName)
}
