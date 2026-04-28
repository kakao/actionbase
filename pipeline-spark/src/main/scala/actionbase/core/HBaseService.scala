package actionbase.core

import actionbase.core.model.HBaseTableSchema
import org.apache.hadoop.hbase.client.Table

trait HBaseService extends Serializable {
  def bulkLoadHadoopConfiguration: org.apache.hadoop.conf.Configuration
  def tableSchema: HBaseTableSchema

  /**
    * Whether this service targets a replication-aware HBase cluster.
    * The OSS pipeline no longer inspects a concrete `ReplicatedHBaseService`
    * subclass; replication awareness is advertised through this flag.
    */
  def isReplicated: Boolean

  def createTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Array[Array[Byte]]
  ): Table
  def exists(namespace: String, tableName: String): Boolean
  def createTable(namespace: String, tableName: String, HBaseTableSchema: HBaseTableSchema): Table
  def getTable(namespace: String, tableName: String): Table
  def recreateTable(
      namespace: String,
      tableName: String,
      hBaseTableSchema: HBaseTableSchema,
      regionSplitKeys: Seq[Array[Byte]]
  ): Table
}
