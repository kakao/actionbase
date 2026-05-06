package com.kakao.actionbase.pipeline.app

import com.kakao.actionbase.pipeline.util.ConfigLoader
import org.apache.spark.sql.SparkSession

import scala.reflect.ClassTag

abstract class AbstractPipelineApplication[T <: Product: ClassTag] {

  def main(args: Array[String]): Unit = {
    println(s"Running ${getClass.getSimpleName}")

    val config = ConfigLoader.load[T](args)

    val spark: SparkSession = configure(
      SparkSession.builder().appName(getClass.getCanonicalName.stripSuffix("$"))
    ).getOrCreate()

    try {
      run(spark, config)
    } finally {
      println("Stopping Spark session...")
      spark.stop()
    }
  }

  protected def run(spark: SparkSession, config: T): Unit

  protected def configure(builder: SparkSession.Builder): SparkSession.Builder = builder
}
