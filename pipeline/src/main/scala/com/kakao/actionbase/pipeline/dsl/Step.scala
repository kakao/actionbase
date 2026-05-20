package com.kakao.actionbase.pipeline.dsl

import org.apache.spark.sql.{DataFrame, SparkSession}

// Shapes: Source 0→1, Flow 1→1, Merge N→1, Split 1→M, Sink 1→0.
sealed trait Step

trait Source extends Step {
  def read()(implicit spark: SparkSession): DataFrame
}

// DataFrame-to-DataFrame middle of the pipeline, varying only by arity (Flow/Merge/Split).
sealed trait Transform extends Step

trait Flow extends Transform {
  def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame
}

// Inputs arrive as (label, df) so consumers (e.g., SqlTransform) can address each by its producer's `as:` label.
trait Merge extends Transform {
  def apply(inputs: Seq[(String, DataFrame)])(implicit spark: SparkSession): DataFrame
  def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame = apply(Seq(DefaultLabel -> in))
}

// Ports declared up-front so the DSL validates references at plan time. Body runs once; ports share the result.
trait Split extends Transform {
  def ports: Seq[String]
  def split(in: DataFrame)(implicit spark: SparkSession): Map[String, DataFrame]
}

trait Sink extends Step {
  def write(in: DataFrame)(implicit spark: SparkSession): Unit
}
