package com.kakao.actionbase.pipeline.app

import org.apache.spark.sql.SparkSession

/** Minimal standalone Spark entry point — useful for ad-hoc exploration in an IDE.
  *
  * Unlike `SparkPiDemo` (which goes through `AbstractPipelineApplication`), this object builds the `SparkSession`
  * directly so the full boilerplate is visible.
  */
object SparkPiMain {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .appName("SparkPiMain")
      .getOrCreate()

    try {
      val pi = SparkPiDemo.estimatePi(spark, slices = 2)
      println(s"Pi is roughly $pi")
    } finally {
      spark.stop()
    }
  }
}
