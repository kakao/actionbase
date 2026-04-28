package actionbase.pipeline.bulkload.step02.splitter.dynamic

import actionbase.core.HFileRegionValidator
import actionbase.core.model.{AbKeyValue, HBaseTableSchema, StorageType}
import actionbase.core.{AbInfo, HBaseService}
import actionbase.pipeline.testsupport.{BaseSparkTest, ContainerBackedHBaseService, HBaseContainer, LocalTestDynamicRegionSplitter}
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.hbase.client.{Admin, Connection, ConnectionFactory, Scan, Table}
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, NamespaceDescriptor, TableName}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.util.SizeEstimator
import org.scalatest.BeforeAndAfterAll

import java.nio.file.{Files, Paths}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.util.Random

/**
 * Integration test for [[DynamicRegionSplitter]] that exercises the full
 * region-split + HFile build path against a live HBase.
 *
 * Task-level gating: `:pipeline:integrationTest` is not wired into
 * `:pipeline:check`, so routine `./gradlew check` / `./gradlew test` runs
 * skip this entirely.
 *
 * The test uses the testcontainers-based [[HBaseContainer]] fixture (introduced
 * in iter-slim-25) instead of the prior in-process `HBaseTestingUtility`
 * mini-cluster. The previous approach failed at startup with
 * `HttpServer2.getWebAppContext()` NoSuchMethodError: Hadoop 2.10.0
 * `HttpServer2` and the HBase 2.5.x shaded mortbay-jetty are
 * binary-incompatible at the classloading layer, and port tuning only
 * prevents instantiation, not linking. A real Docker-run HBase side-steps
 * the entire in-process classpath conflict.
 *
 * The test writes HFiles to a host-local temp directory under `/tmp/` (which
 * Docker Desktop shares with the container by default on macOS). The
 * fixture bind-mounts that same absolute path into the container so any
 * HBase-side reads (e.g. during compaction scheduling, region locator
 * lookups, or future bulkload calls) see an identical filesystem view. The
 * test body itself does not invoke `BulkLoadHFiles`; it validates that
 * produced HFiles map 1:1 to the region key ranges the splitter computed.
 *
 * @note Rewritten for OSS: internal container library replaced first with
 *       HBaseTestingUtility (iter-9) and now with testcontainers-backed
 *       HBaseContainer (iter-slim-26).
 */
class DynamicRegionSplitterIntegrationTest extends BaseSparkTest with BeforeAndAfterAll with StrictLogging {
  // Suite-scoped subdirectory under the JVM-shared container's bind-mount
  // root, so this suite's HFiles cannot collide with other suites even
  // though they share one running HBase.
  private val tmpHdfsRoot = {
    val dir = Files.createTempDirectory(HBaseContainer.sharedRootDir, "dynamic-splitter-it-")
    dir.toFile.deleteOnExit()
    dir.toAbsolutePath.toString
  }
  // defaultFS points at the local fs root — `tmpHdfsRoot` is already absolute
  // and is appended by HdfsMeta.pathToHdfsURI. Keeping them disjoint avoids
  // double-prefixing (e.g. file:///tmp/X/tmp/X/...).
  private val defaultFS   = "file:///"
  private val hbaseTmpDir = s"file://$tmpHdfsRoot/fs_tmp_dir"
  private val namespace   = "test"
  private val table       = "dynamic_splitter_test"
  private val tableName   = TableName.valueOf(namespace, table)

  // Test configuration.
  private val testSetSize   = RegionSize(10, RegionSizeUnit.Megabytes)
  private val maxRegionSize = RegionSize(500, RegionSizeUnit.Kilobytes)
  private val numPartitions = 100

  private val container = HBaseContainer.shared()

  private var conf: Configuration        = _
  private var hbaseService: HBaseService = _
  private var connection: Connection     = _
  private var tableInstance: Table       = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    conf = {
      val c = HBaseConfiguration.create(container.hbaseConfiguration)
      c.set("fs.defaultFS", defaultFS)
      c.set("hbase.fs.tmp.dir", hbaseTmpDir)
      c.set("hbase.mapreduce.hfileoutputformat.table.name", s"$namespace:$table")
      FileSystem.get(c)
      c
    }
    connection = ConnectionFactory.createConnection(conf)
    hbaseService = new ContainerBackedHBaseService(connection, conf)
    tableInstance = prepareHBaseTable(hbaseService, connection)
  }

  override def afterAll(): Unit = {
    try {
      Option(tableInstance).foreach(_.close())
      dropSuiteArtifacts()
      Option(connection).foreach(_.close())
    } finally {
      super.afterAll()
    }
  }

  test("DynamicRegionSplitter saves HFiles that map 1:1 to region ranges") {
    val ds       = generateTestDataset(targetSizeBytes = testSetSize.toBytes.toLong)
    val rowCount = ds.count()
    info(s"Generated $rowCount rows (target size: ${testSetSize.value} ${testSetSize.unit.abb})")

    val testAbInfo = AbInfo(
      serviceName = "test",
      aliasName = "dynamic_splitter_test",
      storageType = StorageType.ST3,
      namespace = namespace,
      tableVersion = ZonedDateTime
        .now(java.time.ZoneOffset.ofHours(9))
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    )

    val splitter = new LocalTestDynamicRegionSplitter(
      newAbInfo = testAbInfo,
      tableSchema = HBaseTableSchema.default().copy(compression = "NONE"),
      tmpHdfsRoot = tmpHdfsRoot,
      numPartitionForKeyValueDS = numPartitions,
      maxRegionSize = maxRegionSize,
      printDebug = true
    )

    conf.set(HFileOutputFormat2.COMPRESSION_OVERRIDE_CONF_KEY, "none")

    val savedPath = splitter.saveHFiles(hbaseService, ds)
    info(s"HFiles saved to: ${savedPath.hdfsUriPath}")

    val fetchedTable = hbaseService.getTable(namespace, testAbInfo.hBaseTableName)
    validateRegionSplit(fetchedTable)
    validateHFileSplit(fetchedTable, savedPath.hdfsUriPath)
    scanAndPrintSample(fetchedTable, limit = 5)
  }

  /**
   * Drop every table in this suite's namespace, then the namespace itself.
   * Runs in afterAll so the shared HBase container's state stays isolated
   * across suites (shared container is reused for the whole JVM lifetime).
   */
  private def dropSuiteArtifacts(): Unit = {
    Option(connection).foreach { conn =>
      val admin = conn.getAdmin
      try {
        admin.listTableDescriptorsByNamespace(Bytes.toBytes(namespace)).asScala.foreach { td =>
          val tn = td.getTableName
          if (admin.isTableEnabled(tn)) admin.disableTable(tn)
          admin.deleteTable(tn)
        }
        if (admin.listNamespaceDescriptors.exists(_.getName == namespace)) {
          admin.deleteNamespace(namespace)
        }
      } catch {
        case t: Throwable =>
          logger.warn(s"dropSuiteArtifacts encountered an error (ignored): ${t.getMessage}", t)
      } finally admin.close()
    }
  }

  private def validateRegionSplit(table: Table): Unit = {
    val regionLocator = table.getRegionLocator
    val regions       = regionLocator.getAllRegionLocations.asScala
    val expectedMinRegions = (testSetSize.toBytes / maxRegionSize.toBytes).toInt
    info(s"Total regions: ${regions.size} (expectedMin=$expectedMinRegions)")

    // Guard against the obvious regression: splitter produces only the
    // default single region, i.e. no split actually happened.
    assert(regions.size >= 2, s"Splitter produced only ${regions.size} region — no split applied")

    // Sample-based estimator may overshoot or undershoot; a 50% tolerance
    // window is acceptable but keep the absolute lower bound above so a
    // degenerate 1-region outcome can never pass even for tiny datasets.
    assert(
      regions.size >= expectedMinRegions * 0.5,
      s"Region count (${regions.size}) below expected (~$expectedMinRegions)"
    )
  }

  private def validateHFileSplit(table: Table, hfilePath: String): Unit = {
    val regionLocator = table.getRegionLocator
    val regionRanges = regionLocator.getAllRegionLocations.asScala.map { loc =>
      val region = loc.getRegion
      (region.getStartKey, region.getEndKey)
    }.toArray

    val hfiles = listHFileDataFiles(Seq(hfilePath))
    info(s"Total HFiles: ${hfiles.size} (regions: ${regionRanges.length})")

    // Dynamic split's defining invariant: one HFile per region range. If
    // the counts diverge we've lost the 1:1 mapping regardless of whether
    // individual HFiles happen to validate.
    assert(
      hfiles.size == regionRanges.length,
      s"HFile count (${hfiles.size}) != region count (${regionRanges.length}) — 1:1 mapping broken"
    )

    var invalidCount = 0
    hfiles.foreach { file =>
      try {
        HFileRegionValidator.validateHFile(file.toString, regionRanges, conf)
      } catch {
        case _: Exception => invalidCount += 1
      }
    }
    assert(invalidCount == 0, s"$invalidCount HFiles do not belong to exactly one region")
  }

  private def scanAndPrintSample(table: Table, limit: Int): Unit = {
    try {
      val scan    = new Scan()
      val scanner = table.getScanner(scan)
      var count   = 0
      scanner.forEach { result =>
        if (count < limit) {
          count += 1
          info(s"Row: ${Bytes2Hex(result.getRow)}")
        }
      }
      scanner.close()
    } finally {
      table.close()
    }
  }

  private def Bytes2Hex(bytes: Array[Byte]): String = {
    if (bytes == null || bytes.isEmpty) "(empty)" else bytes.map("%02x".format(_)).mkString
  }

  private def listHFileDataFiles(inputPaths: Seq[String]): Seq[Path] = {
    def listDataFilesRecursively(pathString: String): Seq[Path] = {
      val fs = FileSystem.get(conf)
      if (!fs.exists(new Path(pathString))) return Seq.empty
      val path     = new Path(pathString)
      val statuses = fs.listStatus(path)

      statuses.flatMap {
        case status if status.isDirectory =>
          listDataFilesRecursively(status.getPath.toString)
        case status if status.isFile =>
          val name = status.getPath.getName
          if (!name.startsWith(".") && !name.startsWith("_") && !name.contains(".crc")) Some(status.getPath)
          else None
        case _ => None
      }
    }

    inputPaths.flatMap(listDataFilesRecursively)
  }

  private def prepareHBaseTable(hbaseService: HBaseService, connection: Connection): Table = {
    val admin = connection.getAdmin
    if (!admin.listNamespaces().contains(namespace)) {
      admin.createNamespace(NamespaceDescriptor.create(namespace).build())
    }
    if (admin.tableExists(tableName)) {
      admin.disableTable(tableName)
      admin.deleteTable(tableName)
    }
    val hbaseSchema = HBaseTableSchema.default().copy(compression = "NONE")
    hbaseService.createTable(namespace, table, hbaseSchema)
    hbaseService.getTable(namespace, table)
  }

  private def generateTestDataset(targetSizeBytes: Long): Dataset[AbKeyValue] = {
    import spark.implicits._
    val sampleRow = AbKeyValue(
      key = generateRandomBytes(10),
      value = generateRandomBytes(50)
    )
    val rowSize  = SizeEstimator.estimate(sampleRow)
    val rowCount = (targetSizeBytes / rowSize).toInt

    val randomData = (1 to rowCount).map { _ =>
      AbKeyValue(
        key = generateRandomBytes(10),
        value = generateRandomBytes(50)
      )
    }
    randomData.toDS()
  }

  private def generateRandomBytes(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }

}

