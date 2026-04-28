package actionbase.pipeline.bulkload.step02.splitter.dynamic

import actionbase.core.model.{AbKeyValue, HBaseTableSchema}
import actionbase.core.{AbInfo, HBaseRegionPartitioner, HBaseService}
import actionbase.pipeline.bulkload.step02.splitter.HBaseRegionSplitter
import actionbase.pipeline.bulkload.step02.splitter.HBaseRegionSplitter.byteArrayOrdering
import actionbase.pipeline.adapter.HdfsMeta
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.hbase.KeyValue
import org.apache.hadoop.hbase.client.{HTable, Table}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.Partitioner
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToOrderedRDDFunctions
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.math.BigDecimal.RoundingMode

/**
  * Splitter that derives region split keys dynamically to bound region size.
  *   - estimate HFile size up-front, group HFiles to fit `maxRegionSize`
  *   - use each group's start key as the region split key
  *   - recreate the table with those split keys, then save the grouped HFiles
  *   - effect: avoids post-bulkload compaction / region splits
  *
  * @param maxRegionSize target region size (default 9GB)
  * @param numPartitionForKeyValueDS partition count used while grouping —
  *                                  too few makes region sizing inaccurate,
  *                                  too many adds overhead
  */
class DynamicRegionSplitter(
    override val newAbInfo: AbInfo,
    override val tableSchema: HBaseTableSchema,
    override val targetHDFSMeta: HdfsMeta,
    override protected val tmpRootPath: String,
    private val numPartitionForKeyValueDS: Int,
    // ~10GB region cap with ~1GB buffer.
    private val maxRegionSize: RegionSize = RegionSize(9000, RegionSizeUnit.Megabytes),
    private val printDebug: Boolean = false,
    private val qualifier: Array[Byte] = AbInfo.DEFAULT_QUALIFIER
)(implicit spark: SparkSession)
    extends HBaseRegionSplitter {
  import DynamicRegionSplitter._
  import org.apache.spark.sql.functions._
  import spark.implicits._

  override protected def createHTable(hbaseService: HBaseService): Table =
    hbaseService.createTable(
      namespace = newAbInfo.namespace,
      tableName = newAbInfo.hBaseTableName,
      HBaseTableSchema = tableSchema
    )

  /**
    * @param keyValueDs unsorted KeyValue Dataset; must be reusable across
    *                   multiple actions.
    * @return saved HFile path
    */
  override def doSaveHFiles(hbaseService: HBaseService, table: Table, keyValueDs: Dataset[AbKeyValue]): String = {

    // Step1: sort keyValueDs and persist as parquet for the grouping pass.
    val pathSorted = tmpPath("split_sorted_parquet")
    saveSortedParquetForGrouping(pathSorted, keyValueDs)

    // Step2: estimate HFile size from the largest sorted parquet file. Use
    // the parquet/HFile ratio to derive a per-region parquet size limit.
    val sampleOriginFilePath = spark.read
      .parquet(pathSorted)
      .inputFiles
      .max(
        (a: String, b: String) => getHDFSPathSize(a).compareTo(getHDFSPathSize(b))
      )
    val sampleForEstimation  = estimateHFileSizeFromParquetSample(hbaseService, table, sampleOriginFilePath)
    val parquetFileSizeLimit = sampleForEstimation.calculatedParquetFileSizeLimit(maxRegionSize)
    printLog(s"Calculated Parquet File Size Limit per Region: ${RegionSize.toMB(parquetFileSizeLimit, 2).value} MB")

    // Step3: group sorted parquet files by the size limit; chunk start keys
    // become region split keys.
    val groupedMeta = groupParquetFilesBySizeLimit(
      pathSorted,
      parquetFileSizeLimit
    )

    // Step4: recreate table with the grouped split keys (drop the first
    // boundary; only inner boundaries are split keys).
    val splitKeys = groupedMeta.chunks.map(_.startKey).drop(1)
    val recreatedTable = hbaseService.recreateTable(
      newAbInfo.namespace,
      newAbInfo.hBaseTableName,
      tableSchema,
      splitKeys
    )

    // Step5: save HFiles aligned to the grouped chunks.
    saveHFilesByGroupedMeta(
      hbaseService,
      recreatedTable,
      groupedMeta,
      tmpHFileUriPath
    )
    tmpHFileUriPath
  }

  private def saveHFilesByGroupedMeta(
      hbaseService: HBaseService,
      table: Table,
      groupedMeta: GroupedMeta,
      hdfsUri: String
  ): Unit = {
    val chunks                                                  = groupedMeta.chunks
    val metaWithIndex: Seq[(LexicalOrderKeyValueMetaList, Int)] = chunks.zipWithIndex
    val pathToPartitionId: Map[String, Int] =
      metaWithIndex.flatMap {
        case (chunk, idx) =>
          chunk.files.map(f => f.path -> idx)
      }.toMap

    printPartitionDebug(chunks, metaWithIndex, pathToPartitionId)

    val sortedDS = spark.read
      .parquet(groupedMeta.chunkSavedPath)
      .withColumn("sourcePath", input_file_name())
      .as[AbKeyValueWrapper]

    val keyedRdd: RDD[((Int, Array[Byte]), Array[Byte])] = sortedDS.rdd.map { x =>
      val partitionId = pathToPartitionId(x.sourcePath)
      ((partitionId, x.key), x.value)
    }

    val partitioner = new Partitioner {
      override def numPartitions: Int          = chunks.size
      override def getPartition(key: Any): Int = key.asInstanceOf[(Int, Array[Byte])]._1
    }

    val hfileRdd = keyedRdd
      .repartitionAndSortWithinPartitions(partitioner)
      .map {
        case ((_, key), value) =>
          val k = new ImmutableBytesWritable(key)
          val v = new KeyValue(key, tableSchema.columnFamilyName, qualifier, value)
          (k, v)
      }

    saveHFileRddToHdfs(hbaseService, table, hfileRdd, hdfsUri, configureForDynamicSplit)
  }

  private val configureForDynamicSplit: Configuration => Unit = { conf =>
    // Reduce spill.
    conf.set("mapreduce.map.memory.mb", "10240")
    conf.set("mapreduce.task.io.sort.mb", "1024")
    conf.set("mapreduce.map.sort.spill.percent", "0.9")

    // Inflate to 500GB (50x default) to avoid unintended HFile splits.
    // HFile-split sizing uses uncompressed in-memory size, while region-split
    // sizing uses on-disk compressed size — both consult this key in HBase
    // 2.5.11, so we lift it well above realistic HFile sizes.
    conf.set("hbase.hregion.max.filesize", "536870912000")
  }

  private def printPartitionDebug(
      chunks: Seq[LexicalOrderKeyValueMetaList],
      metaWithIndex: Seq[(LexicalOrderKeyValueMetaList, Int)],
      pathToPartitionId: Map[String, Int]
  ): Unit = if (printDebug) {
    val metaWithIndexString = metaWithIndex
      .map {
        case (meta, idx) =>
          s"[$idx] ${meta.startKey.mkString(",")} (${RegionSize.toMB(meta.totalSize, 2).value} MB)"
      }
      .mkString("\n")

    printLog(
      s"""[saveHFiles]
         |chunk count: ${chunks.size}
         |metaWithIndex
         |
         |$metaWithIndexString
         |
         |pathToPartitionId
         |${pathToPartitionId.map { case (path, idx) => s"$idx: $path" }.mkString("\n")}
         |""".stripMargin
    )
  }

  private def groupParquetFilesBySizeLimit(
      sortedDSPath: String,
      parquetFilesSizeLimit: Long
  ): GroupedMeta = {
    val sortedDS: Dataset[AbKeyValue] = spark.read.parquet(sortedDSPath).as[AbKeyValue]
    val sortedMeta = sortedDS
      .select($"key", input_file_name().as("file_path"))
      .groupBy("file_path")
      .agg(min("key").as("start_key"))
      .as[(String, Array[Byte])]
      .map(tuple => {
        val path      = tuple._1
        val key       = tuple._2
        val sizeBytes = getHDFSPathSize(path)
        LexicalOrderKeyValueMeta(
          path = path,
          startKey = key,
          sizeBytes = sizeBytes
        )
      })
      .collect()
      .toSeq
      .sortBy(_.startKey)(Ordering.comparatorToOrdering(Bytes.BYTES_COMPARATOR))

    // SteppingSplitPolicy: a single region splits at memstore flush size * 2
    // (256MB). Force at least 2 regions to avoid that.
    val chunks = LexicalOrderKeyValueMetaList.groupBySizeLimit(sortedMeta, parquetFilesSizeLimit) match {
      case single if single.size == 1 && sortedMeta.size >= 2 =>
        val halfSize                = sortedMeta.size / 2
        val (firstHalf, secondHalf) = sortedMeta.splitAt(halfSize)
        Seq(
          LexicalOrderKeyValueMetaList(firstHalf),
          LexicalOrderKeyValueMetaList(secondHalf)
        )
      case result => result
    }

    val groupedMeta = GroupedMeta(
      parquetFilesSizeLimit = parquetFilesSizeLimit,
      chunkSavedPath = sortedDSPath,
      chunks = chunks
    )

    printLog(
      s"""[GroupedMeta]
         |- File Count: ${groupedMeta.chunks.size}
         |- Total Size: ${RegionSize.toMB(groupedMeta.totalSize, 2).value} MB
         |""".stripMargin
    )

    groupedMeta
  }

  private def estimateHFileSizeFromParquetSample(
      hbaseService: HBaseService,
      table: Table,
      sampleParquetPath: String
  )(implicit spark: SparkSession): SampleForEstimation = {
    val sampleJob = Job.getInstance(hbaseService.bulkLoadHadoopConfiguration, "SampleHFile Generator")
    HFileOutputFormat2.configureIncrementalLoad(sampleJob, table, table.asInstanceOf[HTable].getRegionLocator)

    import spark.implicits._
    val hdfsPath = tmpPath("split_sample_hfile")
    val rdd = spark.read
      .parquet(sampleParquetPath)
      .as[AbKeyValue]
      .rdd
      .sortBy(_.key, ascending = true, numPartitions = 1)

    rdd
      .map { kv =>
        (
          new ImmutableBytesWritable(kv.key),
          new KeyValue(kv.key, tableSchema.columnFamilyName, qualifier, kv.value)
        )
      }
      .saveAsNewAPIHadoopFile(
        hdfsPath,
        classOf[ImmutableBytesWritable],
        classOf[KeyValue],
        classOf[HFileOutputFormat2],
        sampleJob.getConfiguration
      )
    allowAllPermissionForHBase(hdfsPath, hbaseService.bulkLoadHadoopConfiguration)
    val estimation = SampleForEstimation(
      parquetPath = sampleParquetPath,
      parquetSizeByte = getHDFSPathSize(sampleParquetPath),
      hfileHdfsUri = hdfsPath,
      hfileSizeByte = getHDFSPathSize(hdfsPath)
    )

    printLog(s"""
                |=================== DynamicRegionSplitter Estimation Result ===================
                |- Sample Parquet Path: ${estimation.parquetPath}
                |- Sample Parquet Size: ${RegionSize.toMB(estimation.parquetSizeByte, 2).value} MB
                |- Sample RDD number of partitions: ${rdd.getNumPartitions}
                |- Sample HFile Path: ${estimation.hfileHdfsUri}
                |- Sample HFile Size: ${RegionSize.toMB(estimation.hfileSizeByte, 2).value} MB
                |- Estimated Compression Ratio: ${BigDecimal(
                  estimation.parquetSizeByte.toDouble / estimation.hfileSizeByte.toDouble
                ).setScale(2, RoundingMode.HALF_EVEN).toDouble}
                |===============================================================================
                |""".stripMargin)
    estimation
  }

  private def getHDFSPathSize(path: String): Long = {
    val hdfsPath       = new Path(path)
    val fs             = FileSystem.get(hdfsPath.toUri, new Configuration())
    val contentSummary = fs.getContentSummary(hdfsPath)
    contentSummary.getLength
  }

  protected def saveSortedParquetForGrouping(savePath: String, keyValueDs: Dataset[AbKeyValue]): Unit = {
    val splitKeys   = generateSplitKeysBySample(keyValueDs.rdd.map(_.key))
    val partitioner = HBaseRegionPartitioner(splitKeys)

    val prepareSplitStartAt = System.currentTimeMillis()
    printLog("[prepareSplit-start] preparing split by sorting and saving parquet")

    keyValueDs.rdd
      .map(kv => (kv.key, kv))
      .repartitionAndSortWithinPartitions(partitioner)
      .map(_._2)
      .toDS()
      .write
      .parquet(savePath)

    printLog(
      s"[prepareSplit-end] save ds at $savePath [elapsed ${DynamicRegionSplitter.readableMillis(System.currentTimeMillis() - prepareSplitStartAt)}]"
    )

    val savedDF = spark.read.parquet(savePath).as[AbKeyValue]
    if (printDebug) {
      logger.info(s"savedDS file count: ${savedDF.inputFiles.length}")

      logger.info(s"savedDS file size")
      savedDF.inputFiles.foreach { path =>
        val size = getHDFSPathSize(path)
        logger.info(s"  - ${RegionSize.toMB(size, 2).value} MB | $path")
      }
    }
  }

  private def generateSplitKeysBySample(
      rdd: RDD[Array[Byte]],
      sampleSize: Int = 100 * 1000
  ): Array[Array[Byte]] = {
    val sampled: Array[Array[Byte]] =
      rdd.takeSample(withReplacement = false, sampleSize, seed = numPartitionForKeyValueDS)

    val sorted    = sampled.sorted
    val step      = sorted.length / numPartitionForKeyValueDS
    val splitKeys = (1 until numPartitionForKeyValueDS).map(i => sorted(i * step)).toArray
    splitKeys
  }
}

object DynamicRegionSplitter {
  private[dynamic] case class AbKeyValueWrapper(
      key: Array[Byte],
      value: Array[Byte],
      sourcePath: String
  )

  /** Format an elapsed-ms count with progressively coarser units. */
  private[dynamic] def readableMillis(ttsInMills: Double): String = {
    import scala.concurrent.duration._
    val sec  = (1 second).toMillis
    val min  = (1 minute).toMillis
    val hour = (1 hour).toMillis

    if (ttsInMills < 1000) {
      f"$ttsInMills ms"
    } else if (ttsInMills < 1 * min) {
      val s  = (ttsInMills / sec).toLong
      val ms = (ttsInMills % sec).toLong
      f"$s%02ds $ms%03d"
    } else if (ttsInMills < hour) {
      val m = (ttsInMills / min).toLong
      val s = ((ttsInMills % min) / sec).toLong
      f"$m%02dm $s%02ds"
    } else {
      val h = (ttsInMills / hour).toLong
      val m = ((ttsInMills % hour) / min).toLong
      f"$h%02dh $m%02dm"
    }
  }
}
