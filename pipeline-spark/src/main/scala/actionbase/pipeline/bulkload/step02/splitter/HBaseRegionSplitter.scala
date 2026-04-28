package actionbase.pipeline.bulkload.step02.splitter

import actionbase.core.{AbInfo, HBaseService}
import actionbase.core.model.{AbKeyValue, HBaseTableSchema}
import actionbase.pipeline.adapter.HdfsMeta
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.hadoop.fs.{FileSystem, Path, RemoteIterator}
import org.apache.hadoop.hbase.KeyValue
import org.apache.hadoop.hbase.client.{HTable, Table}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

/**
  * Create the table with offline pre-split regions and save the HFiles into
  * HDFS for bulk-loading. Single-cluster only — HFiles are written to the
  * cluster running the Spark job; cross-cluster delivery is the caller's
  * responsibility (e.g. distcp or a uri-converter wrapper).
  */
trait HBaseRegionSplitter extends Serializable with StrictLogging {
  val newAbInfo: AbInfo
  val tableSchema: HBaseTableSchema
  val targetHDFSMeta: HdfsMeta

  /**
    * Base HDFS directory where pipeline-produced tmp files live on the
    * target cluster. Consumers must provide a value — there's no safe
    * default because the correct path depends on cluster policy (TTL-
    * cleaned scratch directory, tenant-specific path, etc).
    */
  protected def tmpRootPath: String

  lazy val tmpHFilePath: String    = tmpPath("FINAL_HFile")
  lazy val tmpHFileUriPath: String = targetHDFSMeta.pathToHdfsURI(tmpHFilePath)

  def saveHFiles(hbaseService: HBaseService, keyValueDs: Dataset[AbKeyValue]): HBaseRegionSplitter.SavedPath = {
    validate(hbaseService)
    val table = if (hbaseService.exists(newAbInfo.namespace, newAbInfo.hBaseTableName)) {
      hbaseService.getTable(newAbInfo.namespace, newAbInfo.hBaseTableName)
    } else {
      createHTable(hbaseService)
    }
    printLog(s"The HTable[name=${table.getName.getNameAsString}] is created")
    val startAt = System.currentTimeMillis()
    val hdfsUri = doSaveHFiles(hbaseService, table, keyValueDs)
    HBaseRegionSplitter.SavedPath(
      hdfsUriPath = hdfsUri,
      startAt = startAt,
      endAt = System.currentTimeMillis()
    )
  }

  protected def tmpPath(title: String): String =
    s"$tmpRootPath/${title}_${newAbInfo.hBaseTableName}"
  protected def doSaveHFiles(hbaseService: HBaseService, table: Table, keyValueDs: Dataset[AbKeyValue]): String
  protected def createHTable(hbaseService: HBaseService): Table
  protected def printLog(message: String): Unit = logger.info(makeLogMessage(message))

  /**
    * Open up HFile permissions so HBase moves the file via rename instead of
    * copying it during bulk load. Uses FileSystem directly to avoid coupling
    * to an external HDFS util.
    */
  protected def allowAllPermissionForHBase(hdfsUriPath: String, conf: Configuration): Unit =
    Try {
      val hdfsPath = new Path(hdfsUriPath)
      val fs       = FileSystem.get(hdfsPath.toUri, conf)
      HBaseRegionSplitter.listAllPaths(fs, hdfsPath).foreach { p =>
        fs.setPermission(p, new FsPermission("777"))
      }
    } match {
      case Success(_) =>
        printLog(s"[allowAllPermission] set permission 777 to $hdfsUriPath successfully.")
      case Failure(ex) =>
        printLog(s"[allowAllPermission] failed to set permission 777 to $hdfsUriPath: ${ex.getMessage}")
        ex.printStackTrace()
    }

  /** Save an HFile RDD to HDFS and open up its permissions for HBase. */
  protected def saveHFileRddToHdfs(
      hbaseService: HBaseService,
      table: Table,
      hfileRdd: RDD[(ImmutableBytesWritable, KeyValue)],
      hdfsUri: String,
      jobConfCustomizer: Configuration => Unit = _ => ()
  ): Unit = {
    val job = Job.getInstance(hbaseService.bulkLoadHadoopConfiguration, "HFile Generator")
    HFileOutputFormat2.configureIncrementalLoad(job, table, table.asInstanceOf[HTable].getRegionLocator)

    jobConfCustomizer(job.getConfiguration)

    printLog(s"[saveHFileRddToHdfs] saving HFile to $hdfsUri")

    hfileRdd.saveAsNewAPIHadoopFile(
      hdfsUri,
      classOf[ImmutableBytesWritable],
      classOf[KeyValue],
      classOf[HFileOutputFormat2],
      job.getConfiguration
    )

    allowAllPermissionForHBase(hdfsUri, job.getConfiguration)
    printLog(s"[saveHFileRddToHdfs] completed saving HFile to $hdfsUri")
  }

  /** Convert an AbKeyValue to the HFile KeyValue format. */
  protected def toHFileKeyValue(
      kv: AbKeyValue,
      columnFamily: Array[Byte],
      qualifier: Array[Byte]
  ): (ImmutableBytesWritable, KeyValue) = {
    val key   = kv.key
    val value = kv.value
    (new ImmutableBytesWritable(key), new KeyValue(key, columnFamily, qualifier, value))
  }

  private def loggingTime: String =
    ZonedDateTime.now(HBaseRegionSplitter.KST).format(HBaseRegionSplitter.LOG_TIME_FORMATTER)
  private def makeLogMessage(message: String): String =
    s"[$loggingTime][${getClass.getSimpleName.stripSuffix("$")}] $message"
  private[splitter] def validate(hbaseService: HBaseService): Unit = {
    // Fail fast if the output dir is left over from a prior run.
    val outputPath = new Path(tmpHFileUriPath)
    val fs         = FileSystem.get(outputPath.toUri, new Configuration())
    if (fs.exists(outputPath)) {
      val errorMsg = s"Output path already exists: $tmpHFileUriPath. " +
        s"Remove it before re-running, or it will fail after hours of processing."
      printLog(s"[validate] ERROR: $errorMsg")
      throw new IllegalStateException(errorMsg)
    }

    // Replicated tables require a replication-aware HBaseService adapter.
    if (tableSchema.replicationScope != 0 && !hbaseService.isReplicated) {
      val errorMsg = s"tableSchema.replicationScope is ${tableSchema.replicationScope}, " +
        s"but hBaseService.isReplicated=false"
      printLog(s"[validate] ERROR: $errorMsg")
      throw new IllegalStateException(errorMsg)
    }
  }
}

object HBaseRegionSplitter {
  implicit val byteArrayOrdering: Ordering[Array[Byte]] = (x: Array[Byte], y: Array[Byte]) => Bytes.compareTo(x, y)

  case class SavedPath(
      hdfsUriPath: String,
      startAt: Long,
      endAt: Long
  )

  private val KST: java.time.ZoneOffset = java.time.ZoneOffset.ofHours(9)
  private val LOG_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  /**
    * Recursively list every path (files + directories) under `base`, using
    * `fs.listStatus`. Inlined from the former `HDFSUtil.listFiles(fs, path,
    * recursive=true, includeDir=true)` helper to preserve the original
    * semantics (Hadoop's `fs.listFiles(path, true)` returns *files only* and
    * would miss intermediate directories that also need `777` permission).
    */
  private[splitter] def listAllPaths(fs: FileSystem, base: Path): Seq[Path] = {
    val builder = Seq.newBuilder[Path]
    def aux(path: Path): Unit = {
      val statuses =
        try fs.listStatus(path)
        catch { case _: java.io.FileNotFoundException => Array.empty[org.apache.hadoop.fs.FileStatus] }
      statuses.foreach { s =>
        val p = s.getPath
        builder += p
        if (s.isDirectory) aux(p)
      }
    }
    aux(base)
    builder.result()
  }
}
