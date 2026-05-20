package com.kakao.actionbase.pipeline.steps.source

import com.kakao.actionbase.pipeline.dsl.Source
import org.apache.spark.sql.{DataFrame, SparkSession}

case class FileSource(
    path: String,
    format: String,
    options: Map[String, String] = Map.empty
) extends Source {
  override def read()(implicit spark: SparkSession): DataFrame =
    spark.read.format(format).options(options).load(path)
}
