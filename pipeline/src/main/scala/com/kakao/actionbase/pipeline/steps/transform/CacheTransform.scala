package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Transform
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.storage.StorageLevel

/** Materializes its input once and reuses it across downstream consumers. Useful when a single upstream feeds two or
  * more transforms (e.g., a "split" expressed as two filter transforms over the same source) — without it, Spark
  * re-executes the upstream lineage per consumer.
  *
  * `level` is any string accepted by `StorageLevel.fromString` (default `MEMORY_AND_DISK`).
  */
case class CacheTransform(level: String = "MEMORY_AND_DISK") extends Transform {
  override def apply(inputs: Seq[(String, DataFrame)])(implicit spark: SparkSession): DataFrame = {
    require(inputs.size == 1, s"CacheTransform expects 1 input, got ${inputs.size}")
    inputs.head._2.persist(StorageLevel.fromString(level))
  }
}
