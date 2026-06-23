package com.kakao.actionbase.pipeline

import org.apache.spark.sql.SparkSession

/**
  * Mix-in for unit tests that need an `implicit SparkSession`. The session is `local[2]` and shared across all
  * `SparkTest` mixers in the JVM (Spark's own `getOrCreate` semantics).
  */
trait SparkTest {
  protected implicit lazy val spark: SparkSession = SparkSession
    .builder()
    .master("local[2]")
    .config("spark.driver.bindAddress", "127.0.0.1")
    .config("spark.ui.enabled", "false")
    .appName(getClass.getSimpleName)
    .getOrCreate()
}
