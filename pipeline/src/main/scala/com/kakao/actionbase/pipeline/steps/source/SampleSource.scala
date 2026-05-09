package com.kakao.actionbase.pipeline.steps.source

import com.kakao.actionbase.pipeline.dsl.Source
import org.apache.spark.sql.functions.rand
import org.apache.spark.sql.{DataFrame, SparkSession}

/** Produces `n` rows of random values uniformly drawn from `[0.0, 1.0)`, one column per name in `columns`. */
case class SampleSource(n: Long, columns: Seq[String]) extends Source {
  require(columns.nonEmpty, "SampleSource requires at least one column")

  override def read()(implicit spark: SparkSession): DataFrame =
    spark.range(n).select(columns.map(name => rand().as(name)): _*)
}
