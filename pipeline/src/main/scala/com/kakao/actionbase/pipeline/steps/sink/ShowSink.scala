package com.kakao.actionbase.pipeline.steps.sink

import com.kakao.actionbase.pipeline.dsl.Sink
import org.apache.spark.sql.{DataFrame, SparkSession}

case class ShowSink(numRows: Int = 20, truncate: Boolean = true) extends Sink {
  override def write(in: DataFrame)(implicit spark: SparkSession): Unit =
    in.show(numRows, truncate)
}
