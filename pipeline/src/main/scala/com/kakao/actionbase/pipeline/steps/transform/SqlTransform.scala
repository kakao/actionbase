package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Transform
import org.apache.spark.sql.{DataFrame, SparkSession}

/** Runs `query` against the labeled input DataFrames. Each input is registered as a temp view named after its label —
  * the producer's `as:` (or `"_0"` for the chain default). No separate `views:` parameter — the SQL refers to inputs by
  * the same names that wired them in.
  *
  * Single-input chain: `SqlTransform("SELECT ... FROM _0")`. Join: pair two labeled FileSources with `inputs: [users,
  * events]`, then `SqlTransform("SELECT ... FROM users JOIN events USING (id)")`.
  */
case class SqlTransform(query: String) extends Transform {
  override def apply(inputs: Seq[(String, DataFrame)])(implicit spark: SparkSession): DataFrame = {
    inputs.foreach { case (name, df) => df.createOrReplaceTempView(name) }
    spark.sql(query)
  }
}
