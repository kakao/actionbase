package actionbase.pipeline.bulkload.step02.splitter.uniform

import actionbase.core.model.{AbKeyValue, HBaseTableSchema, StorageType}
import actionbase.core.{AbInfo, HBaseService}
import actionbase.pipeline.adapter.HdfsMeta
import actionbase.pipeline.bulkload.step04.HBaseBulkLoader
import actionbase.pipeline.testsupport.{BaseSparkTest, ContainerBackedHBaseService, HBaseContainer}
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Scan, Table}
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, NamespaceDescriptor}
import org.apache.spark.sql.Dataset
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter
import scala.util.Random

/**
 * Integration test for [[UniformRegionSplitter]]. Uniform split takes an
 * explicit `numRegions` instead of sampling the data, so the test asserts
 * the exact region count produced, then runs the full
 * HFile-write → bulkload → scan flow so the splitter is exercised
 * end-to-end against a live HBase (provided by the testcontainers
 * [[HBaseContainer]] fixture shared with the other IT suites).
 *
 * Task-level gating: `:pipeline:integrationTest` is not wired into
 * `:pipeline:check`; invoke explicitly to run.
 */
class UniformRegionSplitterIntegrationTest extends BaseSparkTest with BeforeAndAfterAll with StrictLogging {
  private val tmpHdfsRoot = {
    val dir = Files.createTempDirectory(HBaseContainer.sharedRootDir, "uniform-splitter-it-")
    dir.toFile.deleteOnExit()
    dir.toAbsolutePath.toString
  }
  private val defaultFS   = "file:///"
  private val hbaseTmpDir = s"file://$tmpHdfsRoot/fs_tmp_dir"
  private val namespace   = "uniform"
  private val table       = "uniform_splitter_test"

  private val numRegions     = 8
  private val splitPerRegion = 1

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
      c.set("hbase.bulkload.staging.dir", s"$tmpHdfsRoot/staging")
      FileSystem.get(c)
      c
    }
    connection = ConnectionFactory.createConnection(conf)
    hbaseService = new ContainerBackedHBaseService(connection, conf)

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

  test(s"UniformRegionSplitter creates exactly $numRegions regions and bulkload round-trips all rows") {
    val ds: Dataset[AbKeyValue] = generateTestDataset(rowCount = 500)
    val rowCount                = ds.count()
    info(s"Generated $rowCount rows")

    val testAbInfo = AbInfo(
      serviceName = "uniform",
      aliasName = "uniform_splitter_test",
      storageType = StorageType.ST3,
      namespace = namespace,
      tableVersion = ZonedDateTime
        .now(java.time.ZoneOffset.ofHours(9))
        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    )

    // Top-level splitter subclass that overrides tmpRootPath for the test — same
    // closure-serialization reason LocalTestDynamicRegionSplitter has one: an
    // anonymous or inner subclass would capture the ScalaTest engine via $outer.
    val splitter = new UniformRegionSplitterIntegrationTest.LocalTestUniformRegionSplitter(
      newAbInfo = testAbInfo,
      tableSchema = HBaseTableSchema.default().copy(compression = "NONE"),
      tmpHdfsRoot = tmpHdfsRoot,
      numRegions = numRegions,
      splitPerRegion = splitPerRegion
    )
    conf.set(HFileOutputFormat2.COMPRESSION_OVERRIDE_CONF_KEY, "none")
    val savedPath = splitter.saveHFiles(hbaseService, ds)
    info(s"HFiles saved to: ${savedPath.hdfsUriPath}")

    // Assert region count matches the explicit `numRegions`. HBase creates
    // numRegions regions for numRegions-1 split keys (UniformSplit returns
    // numRegions-1 split keys when called with `numRegions`).
    val createdTable = hbaseService.getTable(namespace, testAbInfo.hBaseTableName)
    try {
      val regions = createdTable.getRegionLocator.getAllRegionLocations.asScala
      info(s"Table has ${regions.size} regions (expected=$numRegions)")
      assert(regions.size == numRegions, s"Uniform split produced ${regions.size} regions, expected $numRegions")
    } finally createdTable.close()

    // Bulkload the HFiles and verify row count round-trip.
    val loader = new HBaseBulkLoader
    loader.execute(testAbInfo, savedPath.hdfsUriPath, conf)

    val loadedTable: Table = hbaseService.getTable(namespace, testAbInfo.hBaseTableName)
    try {
      val scanner = loadedTable.getScanner(new Scan())
      var scanned = 0L
      try {
        val it = scanner.iterator()
        while (it.hasNext) { it.next(); scanned += 1 }
      } finally scanner.close()

      info(s"Scanned $scanned rows from HBase after bulkload (generated=$rowCount)")
      assert(scanned == rowCount, s"Row count mismatch: generated=$rowCount, scanned=$scanned")
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

  private def generateTestDataset(rowCount: Int): Dataset[AbKeyValue] = {
    import spark.implicits._
    val data = (1 to rowCount).map { _ =>
      AbKeyValue(key = randomBytes(10), value = randomBytes(50))
    }
    data.toDS()
  }

  private def randomBytes(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }
}

object UniformRegionSplitterIntegrationTest {

  /** Test variant that passes `tmpRootPath` through to the parent so HFiles
   *  land under the shared bind-mount root. Top-level to avoid capturing
   *  ScalaTest `Engine` via `$outer` in Spark closures. */
  private[uniform] class LocalTestUniformRegionSplitter(
      override val newAbInfo: AbInfo,
      override val tableSchema: HBaseTableSchema,
      tmpHdfsRoot: String,
      numRegions: Int,
      splitPerRegion: Int
  ) extends UniformRegionSplitter(
        newAbInfo = newAbInfo,
        tableSchema = tableSchema,
        targetHDFSMeta = HdfsMeta("local-insecure", ""),
        tmpRootPath = tmpHdfsRoot,
        numRegions = numRegions,
        splitPerRegion = splitPerRegion
      )
}
