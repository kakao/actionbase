package com.kakao.actionbase.pipeline.app

import com.kakao.actionbase.pipeline.util.ConfigLoader
import org.apache.spark.sql.SparkSession
import scala.reflect.ClassTag

abstract class AbstractPipelineApplication[T <: Product: ClassTag] {

  def main(args: Array[String]): Unit = {
    println(s"Running ${getClass.getSimpleName}")

    val config = ConfigLoader.load[T](args)

    val spark: SparkSession = SparkSession
      .builder()
      .appName(getClass.getCanonicalName.stripSuffix("$"))
      .enableHiveSupport()
      .getOrCreate()

    try {
      run(spark, config)
    } finally {
      println("Stopping Spark session...")
      spark.stop()
    }
  }

  def run(spark: SparkSession, config: T): Unit
}
