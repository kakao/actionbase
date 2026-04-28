package actionbase.pipeline.bulkload.step02.splitter

import actionbase.core.model.{AbKeyValue, HBaseTableSchema}
import actionbase.core.{AbInfo, HBaseService}
import actionbase.core.model.StorageType
import actionbase.pipeline.adapter.HdfsMeta
import org.apache.hadoop.hbase.client.Table
import org.apache.spark.sql.Dataset
import actionbase.pipeline.testsupport.BaseTest

import java.io.File

class HBaseRegionSplitterTest extends BaseTest {

  private val testAbInfo = AbInfo(
    serviceName = "test_db",
    aliasName = "test_alias",
    storageType = StorageType.ST4,
    namespace = "test_ns",
    tableVersion = "20250101_120000"
  )

  private val testTableSchema = HBaseTableSchema.default()

  private class TestSplitter(outputPath: String) extends HBaseRegionSplitter {
    override val newAbInfo: AbInfo                = testAbInfo
    override val tableSchema: HBaseTableSchema    = testTableSchema
    override val targetHDFSMeta: HdfsMeta         = HdfsMeta("testCluster", "hdfs://testCluster")
    override protected val tmpRootPath: String    = "/tmp/test-splitter"
    override lazy val tmpHFileUriPath: String     = outputPath

    override protected def doSaveHFiles(
        hbaseService: HBaseService,
        table: Table,
        keyValueDs: Dataset[AbKeyValue]
    ): String = ???

    override protected def createHTable(hbaseService: HBaseService): Table = ???
  }

  private val dummyHBaseService: HBaseService = new HBaseService {
    override def bulkLoadHadoopConfiguration                                                     = ???
    override def tableSchema                                                                     = testTableSchema
    override def isReplicated: Boolean                                                           = false
    override def createTable(ns: String, tn: String, s: HBaseTableSchema, k: Array[Array[Byte]]) = ???
    override def exists(ns: String, tn: String)                                                  = ???
    override def createTable(ns: String, tn: String, s: HBaseTableSchema)                        = ???
    override def getTable(ns: String, tn: String)                                                = ???
    override def recreateTable(ns: String, tn: String, s: HBaseTableSchema, k: Seq[Array[Byte]]) = ???
  }

  test("validate throws IllegalStateException when the output path already exists") {
    val outputDir = new File(s"$tempRootDir/test_hfile_output_exists")
    outputDir.mkdirs()
    try {
      val splitter = new TestSplitter(s"file://${outputDir.getAbsolutePath}")
      val ex = the[IllegalStateException] thrownBy {
        splitter.validate(dummyHBaseService)
      }
      ex.getMessage should include("Output path already exists")
    } finally {
      outputDir.delete()
    }
  }

  test("validate passes when the output path does not exist") {
    val outputPath = s"file://$tempRootDir/test_hfile_output_not_exists_${System.nanoTime()}"
    val splitter   = new TestSplitter(outputPath)

    noException should be thrownBy {
      splitter.validate(dummyHBaseService)
    }
  }
}
