package actionbase.core

import actionbase.core.model.AbKeyValue
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.RegionLocator
import org.apache.hadoop.hbase.io.hfile.CacheConfig
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.SparkSession

object HFileRegionValidator {
  case class FileKeyRange(filePath: String, startKey: Array[Byte], endKey: Array[Byte])

  object SerializableBytesOrdering extends Ordering[Array[Byte]] with Serializable {
    override def compare(a: Array[Byte], b: Array[Byte]): Int =
      Bytes.compareTo(a, b)
  }

  def extractKeyRangeFromParquet(
      parquetDir: String
  )(implicit spark: SparkSession): Seq[FileKeyRange] = {
    import spark.implicits._
    val files = spark.read.parquet(parquetDir).inputFiles

    files.map { path =>
      val ds    = spark.read.parquet(path).as[AbKeyValue]
      val rdd   = ds.rdd.map(_.key)
      val start = rdd.min()(SerializableBytesOrdering)
      val end   = rdd.max()(SerializableBytesOrdering)
      FileKeyRange(path, start, end)
    }
  }

  def loadRegionRanges(locator: RegionLocator): Seq[(Array[Byte], Array[Byte])] = {
    import scala.jdk.CollectionConverters._
    locator.getAllRegionLocations.asScala.map { loc =>
      val region = loc.getRegion
      (region.getStartKey, region.getEndKey)
    }
  }

  def belongsToOneRegion(
      start: Array[Byte],
      end: Array[Byte],
      regions: Seq[(Array[Byte], Array[Byte])]
  ): Boolean = {
    val covered = regions.count {
      case (rStart, rEnd) =>
        val afterStart = Bytes.BYTES_COMPARATOR.compare(start, rStart) >= 0
        val beforeEnd  = rEnd.isEmpty || Bytes.BYTES_COMPARATOR.compare(end, rEnd) < 0
        afterStart && beforeEnd
    }
    covered == 1
  }

  def validateParquet(
      parquetDir: String,
      locator: RegionLocator
  )(implicit spark: SparkSession): Unit = {
    val regionRanges = loadRegionRanges(locator)
    val fileRanges   = extractKeyRangeFromParquet(parquetDir)

    val invalid = fileRanges.filterNot { file =>
      belongsToOneRegion(file.startKey, file.endKey, regionRanges)
    }

    if (invalid.isEmpty) {
      println("[parquet] OK: every file maps to exactly one region.")
    } else {
      println(s"[parquet] ${invalid.size} file(s) span multiple regions:")
      invalid.foreach { file =>
        println(s"  [${file.filePath}]")
        println(s"    StartKey: ${Bytes.toHex(file.startKey)}")
        println(s"    EndKey  : ${Bytes.toHex(file.endKey)}")
      }
    }
  }

  def validateHFile(hfilePath: String, regionRanges: Array[(Array[Byte], Array[Byte])], conf: Configuration): Unit = {
    val range = HFileRangeUtil.extractKeyRangeFromHFile(hfilePath, conf)
    def belongsToRegion(
        startKey: Array[Byte],
        endKey: Array[Byte],
        regionStart: Array[Byte],
        regionEnd: Array[Byte]
    ): Boolean = {
      val cmpStart = Bytes.compareTo(startKey, regionStart) >= 0
      val cmpEnd   = regionEnd.isEmpty || Bytes.compareTo(endKey, regionEnd) < 0
      cmpStart && cmpEnd
    }

    val matched = regionRanges.filter {
      case (regionStart, regionEnd) =>
        belongsToRegion(range.startKey, range.endKey, regionStart, regionEnd)
    }

    if (matched.length != 1) {
      println(s"[hfile] file spans ${matched.length} regions:")
    } else {
      println("[hfile] OK: file maps to exactly one region.")
    }
  }

  import org.apache.hadoop.conf.Configuration
  import org.apache.hadoop.fs.Path
  import org.apache.hadoop.hbase.io.hfile.HFile

  object HFileRangeUtil {
    case class HFileKeyRange(path: String, startKey: Array[Byte], endKey: Array[Byte])
    def extractKeyRangeFromHFile(path: String, conf: Configuration): HFileKeyRange = {
      val fsPath = new Path(path)
      val fs     = fsPath.getFileSystem(conf)

      val reader = HFile
        .createReader(
          fs,
          fsPath,
          new CacheConfig(conf),
          true,
          conf
        )

      try {
        val startKey = Option(reader.getFirstRowKey).map(_.get()).getOrElse(Array.emptyByteArray)
        val endKey   = Option(reader.getLastRowKey).map(_.get()).getOrElse(Array.emptyByteArray)
        HFileKeyRange(path, startKey, endKey)
      } finally {
        reader.close()
      }
    }
  }
}
