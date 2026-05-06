package com.kakao.actionbase.pipeline.app

import org.apache.spark.sql.SparkSession

case class SparkPiConfig(slices: Int = 2)

object SparkPiDemo extends AbstractPipelineApplication[SparkPiConfig] {

  override def run(spark: SparkSession, config: SparkPiConfig): Unit = {
    val pi = estimatePi(spark, config.slices)
    println(s"Pi is roughly $pi")
  }

  def estimatePi(spark: SparkSession, slices: Int): Double = {
    val n = math.min(100000L * slices, Int.MaxValue).toInt
    val count = spark.sparkContext
      .parallelize(1 until n, slices)
      .map { _ =>
        val x = math.random * 2 - 1
        val y = math.random * 2 - 1
        if (x * x + y * y <= 1) 1 else 0
      }
      .reduce(_ + _)
    4.0 * count / (n - 1)
  }
}
