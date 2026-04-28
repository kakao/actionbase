package actionbase.pipeline.bulkload.step02.splitter.uniform

import actionbase.core.model.{AbKeyValue, HBaseTableSchema}
import actionbase.core.{AbInfo, HBaseRegionPartitioner, HBaseService}
import actionbase.pipeline.adapter.HdfsMeta
import actionbase.pipeline.bulkload.step02.splitter.HBaseRegionSplitter
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.util.RegionSplitter
import org.apache.spark.rdd.RDD.rddToOrderedRDDFunctions
import org.apache.spark.sql.Dataset

/**
  * Uniform region splitter — creates a fixed number of evenly-spaced regions
  * using HBase's built-in `RegionSplitter.UniformSplit`, regardless of the
  * actual data distribution. Useful when the caller already knows the
  * desired region count up front (e.g. matching a downstream cluster's
  * region count) and does not need data-driven region sizing.
  *
  * Contrast with `DynamicRegionSplitter`, which samples the dataset and
  * groups HFiles to target a `maxRegionSize` budget.
  */
class UniformRegionSplitter(
    override val newAbInfo: AbInfo,
    override val tableSchema: HBaseTableSchema,
    override val targetHDFSMeta: HdfsMeta,
    override protected val tmpRootPath: String,
    private val numRegions: Int,
    private val splitPerRegion: Int,
    private val qualifier: Array[Byte] = AbInfo.DEFAULT_QUALIFIER
) extends HBaseRegionSplitter {
  import HBaseRegionSplitter.byteArrayOrdering

  override protected def createHTable(hbaseService: HBaseService): Table = {
    val regionSplitKeys = new RegionSplitter.UniformSplit().split(numRegions)
    hbaseService.createTable(newAbInfo.namespace, newAbInfo.hBaseTableName, tableSchema, regionSplitKeys)
  }

  override protected def doSaveHFiles(
      hbaseService: HBaseService,
      table: Table,
      keyValueDs: Dataset[AbKeyValue]
  ): String = {
    val customPartitioner = HBaseRegionPartitioner(table.getRegionLocator, splitPerRegion)
    printLog(customPartitioner.toString)

    // 1. sort + repartition by row key
    // 2. convert to HFile KeyValue
    val hFileRdd = keyValueDs.rdd
      .map(kv => (kv.key, kv))
      .repartitionAndSortWithinPartitions(customPartitioner)
      .map { case (_, kv) => toHFileKeyValue(kv, tableSchema.columnFamilyName, qualifier) }

    saveHFileRddToHdfs(hbaseService, table, hFileRdd, tmpHFileUriPath)

    tmpHFileUriPath
  }
}
