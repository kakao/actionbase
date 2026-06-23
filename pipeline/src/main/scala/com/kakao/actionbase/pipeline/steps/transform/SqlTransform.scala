package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Merge
import org.apache.spark.sql.{DataFrame, SparkSession}

// Each input registers as a temp view named by its producer's `as:` label (or "_0" for the chain default).
case class SqlTransform(query: String) extends Merge {
  override def apply(inputs: Seq[(String, DataFrame)])(implicit spark: SparkSession): DataFrame = {
    inputs.foreach { case (name, df) => df.createOrReplaceTempView(name) }
    spark.sql(query)
  }
}
