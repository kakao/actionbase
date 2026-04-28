package actionbase.core.model

import actionbase.core.AbInfo
import org.apache.hadoop.hbase.client.{ColumnFamilyDescriptorBuilder, TableDescriptorBuilder}
import org.apache.hadoop.hbase.io.compress.Compression
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding
import org.apache.hadoop.hbase.regionserver.BloomType
import org.apache.hadoop.hbase.{KeepDeletedCells, TableName}

case class HBaseTableSchema(
    columnFamilyName: Array[Byte],
    bloomFilter: String,
    inMemory: Boolean,
    keepDeletedCells: String,
    dataBlockEncoding: String,
    compression: String,
    ttl: String,
    maxVersions: Int,
    minVersions: Int,
    blockCache: Boolean,
    blockSize: Int,
    replicationScope: Int
) {
  def toHBaseTableDescriptor(namespace: String, tableName: String) = {
    TableDescriptorBuilder
      .newBuilder(TableName.valueOf(namespace, tableName))
      .setColumnFamily(
        ColumnFamilyDescriptorBuilder
          .newBuilder(columnFamilyName)
          .setBloomFilterType(BloomType.valueOf(bloomFilter))
          .setInMemory(inMemory)
          .setMinVersions(minVersions)
          .setMaxVersions(maxVersions)
          .setKeepDeletedCells(KeepDeletedCells.valueOf(keepDeletedCells))
          .setDataBlockEncoding(DataBlockEncoding.valueOf(dataBlockEncoding))
          .setCompressionType(Compression.Algorithm.valueOf(compression))
          .setTimeToLive(ttl)
          .setBlockCacheEnabled(blockCache)
          .setBlocksize(blockSize)
          .build()
      )
      .setSplitEnabled(true)
      .setReplicationScope(replicationScope)
      .build()
  }
}

object HBaseTableSchema {
  def default(
      columnFamilyName: Array[Byte] = AbInfo.DEFAULT_COLUMN_FAMILY,
      replication: Boolean = false
  ): HBaseTableSchema =
    HBaseTableSchema(
      columnFamilyName = columnFamilyName,
      bloomFilter = "ROW",
      inMemory = false,
      keepDeletedCells = "FALSE",
      dataBlockEncoding = "FAST_DIFF",
      compression = "LZ4",
      ttl = "forever",
      maxVersions = 1,
      minVersions = 0,
      blockCache = true,
      blockSize = 65536,
      replicationScope = if (replication) 1 else 0
    )
}
