package com.kakao.actionbase.pipeline.app

import com.kakao.actionbase.pipeline.util.ConfigLoader
import org.apache.spark.sql.SparkSession
import scala.reflect.ClassTag

abstract class AbstractPipelineApplication[T <: Product: ClassTag] {

  // Override to opt out of Hive metastore for pipelines that don't need it.
  protected def hiveSupport: Boolean = true

  // Last-mile customization point. Subclasses can override to attach
  // Spark configuration (e.g. .config("spark.sql.shuffle.partitions", ...))
  // without rebuilding the whole main() method.
  protected def configureSparkBuilder(builder: SparkSession.Builder): SparkSession.Builder =
    if (hiveSupport) builder.enableHiveSupport() else builder

  def main(args: Array[String]): Unit = {
    println(s"Running ${getClass.getSimpleName}")

    val config = ConfigLoader.load[T](args)

    val spark: SparkSession = configureSparkBuilder(
      SparkSession.builder().appName(getClass.getCanonicalName.stripSuffix("$"))
    ).getOrCreate()

    try {
      run(spark, config)
    } finally {
      println("Stopping Spark session...")
      spark.stop()
    }
  }

  def run(spark: SparkSession, config: T): Unit
}
