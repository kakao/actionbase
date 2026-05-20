package com.kakao.actionbase.pipeline.steps.sink

import com.kakao.actionbase.pipeline.dsl.Sink
import org.apache.spark.sql.{DataFrame, SparkSession}

// `mode` defaults to `errorifexists` (Spark default) to avoid silent overwrites on accidental rerun.
case class FileSink(
    path: String,
    format: String,
    mode: String = "errorifexists",
    options: Map[String, String] = Map.empty
) extends Sink {
  override def write(in: DataFrame)(implicit spark: SparkSession): Unit =
    in.write.format(format).mode(mode).options(options).save(path)
}
