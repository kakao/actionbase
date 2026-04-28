package actionbase.pipeline.bulkload

import actionbase.core.model.{AbKeyValue, HBaseTableSchema, StorageType}
import actionbase.core.{AbInfo, HBaseService}
import actionbase.pipeline.bulkload.step02.splitter.dynamic.{RegionSize, RegionSizeUnit}
import actionbase.pipeline.bulkload.step04.HBaseBulkLoader
import actionbase.pipeline.testsupport.{
  BaseSparkTest,
  ContainerBackedHBaseService,
  HBaseContainer,
  LocalTestDynamicRegionSplitter
}
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Scan, Table}
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, NamespaceDescriptor}
import org.apache.spark.sql.Dataset
import org.apache.spark.util.SizeEstimator
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.util.Random

/**
 * End-to-end integration test for the bulkload flow. Exercises the full
 * path on a real HBase instance provided by the testcontainers
 * [[HBaseContainer]] fixture:
 *
 *   1. Generate a small in-memory `Dataset[AbKeyValue]`.
 *   2. Write pre-split HFiles via [[LocalTestDynamicRegionSplitter]]
 *      (Dynamic splitter; the same flow also works with Uniform).
 *   3. Load the HFiles into the running HBase with [[HBaseBulkLoader]]
 *      (`BulkLoadHFiles.bulkLoad` under the hood).
 *   4. Scan the table back and assert every generated row is retrievable.
 *
 * Task-level gating: `:pipeline:integrationTest` is not wired into
 * `:pipeline:check`, so routine test runs skip this. Invoke
 * `./gradlew :pipeline:integrationTest` explicitly.
 *
 * Uses the JVM-shared [[HBaseContainer]]; suite-scoped `e2e` namespace
 * is dropped in `afterAll` to keep state isolated.
 */
class BulkLoadEndToEndIntegrationTest extends BaseSparkTest with BeforeAndAfterAll with StrictLogging {
  private val tmpHdfsRoot = {
    val dir = Files.createTempDirectory(HBaseContainer.sharedRootDir, "bulkload-e2e-it-")
    dir.toFile.deleteOnExit()
    dir.toAbsolutePath.toString
  }
  // defaultFS points at the local fs root — `tmpHdfsRoot` is already absolute
  // and is appended by HdfsMeta.pathToHdfsURI. Keeping them disjoint avoids
  // double-prefixing (e.g. file:///tmp/X/tmp/X/...).
  private val defaultFS   = "file:///"
  private val hbaseTmpDir = s"file://$tmpHdfsRoot/fs_tmp_dir"
  private val namespace   = "e2e"
  private val table       = "bulkload_e2e_test"

  // Test configuration — keep small so the E2E suite stays fast.
  private val testSetSize   = RegionSize(2, RegionSizeUnit.Megabytes)
  private val maxRegionSize = RegionSize(500, RegionSizeUnit.Kilobytes)
  private val numPartitions = 50

  private val container = HBaseContainer.shared()

  private var conf: Configuration        = _
  private var hbaseService: HBaseService = _
  private var connection: Connection     = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    conf = {
      val c = HBaseConfiguration.create(container.hbaseConfiguration)
      c.set("fs.defaultFS", defaultFS)
      c.set("hbase.fs.tmp.dir", hbaseTmpDir)
      c.set("hbase.mapreduce.hfileoutputformat.table.name", s"$namespace:$table")
      // Bulkload staging must live under the host↔container shared mount
      // so the RegionServer inside the container can see the HFiles the
      // host JVM produced. Default (`$hbase.rootdir/staging`) resolves
      // to a container-internal path that isn't visible on the host.
      c.set("hbase.bulkload.staging.dir", s"$tmpHdfsRoot/staging")
      FileSystem.get(c)
      c
    }
    connection = ConnectionFactory.createConnection(conf)
    hbaseService = new ContainerBackedHBaseService(connection, conf)

    // Namespace must exist before the splitter calls createTable.
    val admin = connection.getAdmin
    try {
      if (!admin.listNamespaceDescriptors.exists(_.getName == namespace)) {
        admin.createNamespace(NamespaceDescriptor.create(namespace).build())
      }
    } finally admin.close()
  }

  override def afterAll(): Unit = {
    try {
      dropSuiteArtifacts()
      Option(connection).foreach(_.close())
    } finally super.afterAll()
  }

  test("end-to-end: HFiles written by DynamicRegionSplitter load into HBase and all rows are retrievable") {
    val ds       = generateTestDataset(targetSizeBytes = testSetSize.toBytes.toLong)
    val rowCount = ds.count()
    info(s"Generated $rowCount rows (target: ${testSetSize})")

    val testAbInfo = AbInfo(
      serviceName = "e2e",
      aliasName = "bulkload_e2e_test",
      storageType = StorageType.ST3,
      namespace = namespace,
      tableVersion = ZonedDateTime
        .now(java.time.ZoneOffset.ofHours(9))
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    )

    // Step 1-2: write pre-split HFiles
    val splitter = new LocalTestDynamicRegionSplitter(
      newAbInfo = testAbInfo,
      tableSchema = HBaseTableSchema.default().copy(compression = "NONE"),
      tmpHdfsRoot = tmpHdfsRoot,
      numPartitionForKeyValueDS = numPartitions,
      maxRegionSize = maxRegionSize,
      printDebug = false
    )
    conf.set(HFileOutputFormat2.COMPRESSION_OVERRIDE_CONF_KEY, "none")
    val savedPath = splitter.saveHFiles(hbaseService, ds)
    info(s"HFiles saved to: ${savedPath.hdfsUriPath}")

    // Step 3: bulkload via BulkLoadHFiles
    val loader = new HBaseBulkLoader
    loader.execute(testAbInfo, savedPath.hdfsUriPath, conf)

    // Step 4: scan & verify row count
    val loadedTable: Table = hbaseService.getTable(namespace, testAbInfo.hBaseTableName)
    try {
      val scanner = loadedTable.getScanner(new Scan())
      var scanned = 0L
      try {
        val it = scanner.iterator()
        while (it.hasNext) { it.next(); scanned += 1 }
      } finally scanner.close()

      info(s"Scanned $scanned rows from HBase after bulkload (generated=$rowCount)")
      assert(
        scanned == rowCount,
        s"Row count mismatch after bulkload: expected $rowCount but scanned $scanned"
      )

      // Region count sanity check — the splitter should have produced a
      // multi-region layout, and bulkload should have preserved it.
      val regions = loadedTable.getRegionLocator.getAllRegionLocations.asScala
      info(s"Table has ${regions.size} regions after bulkload")
      assert(regions.size >= 2, s"Table has only ${regions.size} region — split did not take effect")
    } finally loadedTable.close()
  }

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

  private def generateTestDataset(targetSizeBytes: Long): Dataset[AbKeyValue] = {
    import spark.implicits._
    val sampleRow = AbKeyValue(key = generateRandomBytes(10), value = generateRandomBytes(50))
    val rowSize   = SizeEstimator.estimate(sampleRow)
    val rowCount  = (targetSizeBytes / rowSize).toInt

    val data = (1 to rowCount).map { _ =>
      AbKeyValue(key = generateRandomBytes(10), value = generateRandomBytes(50))
    }
    data.toDS()
  }

  private def generateRandomBytes(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }
}
