package com.kakao.actionbase.pipeline.steps.source

import com.kakao.actionbase.pipeline.dsl.Source
import org.apache.spark.sql.functions.rand
import org.apache.spark.sql.{DataFrame, SparkSession}

case class SampleSource(n: Long, columns: Seq[String]) extends Source {
  require(columns.nonEmpty, "SampleSource requires at least one column")

  override def read()(implicit spark: SparkSession): DataFrame =
    spark.range(n).select(columns.map(name => rand().as(name)): _*)
}
