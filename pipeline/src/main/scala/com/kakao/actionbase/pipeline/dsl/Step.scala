package com.kakao.actionbase.pipeline.dsl

import org.apache.spark.sql.{DataFrame, SparkSession}

sealed trait Step

trait Source extends Step {
  def read()(implicit spark: SparkSession): DataFrame
}

/** A Transform consumes one or more labeled input DataFrames and produces one. Each input arrives as a
  * `(label, df)` pair so consumers (e.g., `SqlTransform`) can use the label as a temp view name without re-stating
  * it. The label is the producer's `as:` (or `"_0"` for a single-input chain default).
  */
trait Transform extends Step {
  def apply(inputs: Seq[(String, DataFrame)])(implicit spark: SparkSession): DataFrame

  /** Single-input convenience for DSL `~>` and unit tests. The implicit label is `"_0"`. */
  def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame = apply(Seq("_0" -> in))
}

/** A Sink consumes a single DataFrame. Multi-input fan-in isn't a Sink concern — wire multiple sinks as siblings via
  * `fanOut(...)` (Scala DSL) or repeat the sink step under different `inputs:` (YAML).
  */
trait Sink extends Step {
  def write(in: DataFrame)(implicit spark: SparkSession): Unit
}
