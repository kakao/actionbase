package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Flow
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.storage.StorageLevel

// Materialize once when one upstream feeds multiple transforms; without it Spark re-executes the lineage per consumer.
case class CacheTransform(level: String = "MEMORY_AND_DISK") extends Flow {
  override def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame =
    in.persist(StorageLevel.fromString(level))
}
