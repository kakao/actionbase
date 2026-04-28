package actionbase.pipeline.testsupport

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

/**
  * OSS-friendly Spark bootstrap for tests.
  *
  *   - [[LocalSpark]]: thin Spark session factory. Hive support and Iceberg extensions
  *     are intentionally not enabled so the OSS pipeline module runs on a vanilla Spark
  *     distribution; tests that need them must opt in via `withConf`.
  *   - [[BaseSparkTest]]: mixin trait that lazily exposes an implicit `SparkSession`
  *     shared across cases within one suite.
  */
object LocalSpark {

  private def baseConf(appName: String): SparkConf =
    new SparkConf()
      .setAppName(appName)
      .setMaster("local[2]")
      .set("spark.ui.enabled", "false")
      .set("spark.sql.shuffle.partitions", "4")
      .set("spark.driver.bindAddress", "127.0.0.1")
      .set("spark.driver.host", "localhost")
      // Kryo handles non-Serializable classes from `codec-java`
      // (e.g. `BulkLoadEdge`) that the default Java serializer rejects.
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

  def local(appName: String = "pipeline-test"): SparkSession =
    SparkSession.builder().config(baseConf(appName)).getOrCreate()

  def withConf(appName: String)(overrides: (String, String)*): SparkSession = {
    val conf = baseConf(appName)
    overrides.foreach { case (k, v) => conf.set(k, v) }
    SparkSession.builder().config(conf).getOrCreate()
  }
}

trait BaseSparkTest extends BaseTest {
  @transient implicit lazy val spark: SparkSession = LocalSpark.local(appName = getClass.getSimpleName)
}
