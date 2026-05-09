package com.kakao.actionbase.pipeline.steps.sink

import com.kakao.actionbase.pipeline.dsl.Sink
import org.apache.spark.sql.{DataFrame, SparkSession}

/** Writes a DataFrame to file storage using Spark's `DataFrameWriter`. `format` is any Spark-supported short name
  * (`parquet`, `json`, `csv`, `orc`, ...); `options` are passed through to `DataFrameWriter.options`.
  */
case class FileSink(
    path: String,
    format: String,
    mode: String = "overwrite",
    options: Map[String, String] = Map.empty
) extends Sink {
  override def write(in: DataFrame)(implicit spark: SparkSession): Unit =
    in.write.format(format).mode(mode).options(options).save(path)
}
