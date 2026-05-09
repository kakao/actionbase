package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.SparkTest
import org.apache.spark.storage.StorageLevel
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class CacheTransformTest extends SparkTest {

  @Test
  def persistsAtDefaultLevel(): Unit = {
    import spark.implicits._
    val in = Seq(1, 2, 3).toDF("n")

    val out = CacheTransform().apply(in)

    assertEquals(StorageLevel.MEMORY_AND_DISK, out.storageLevel)
    out.unpersist()
  }

  @Test
  def respectsExplicitStorageLevel(): Unit = {
    import spark.implicits._
    val in = Seq(1, 2, 3).toDF("n")

    val out = CacheTransform(level = "MEMORY_ONLY").apply(in)

    assertEquals(StorageLevel.MEMORY_ONLY, out.storageLevel)
    out.unpersist()
  }

  @Test
  def passesRowsThroughUnchanged(): Unit = {
    import spark.implicits._
    val in = Seq(("a", 1), ("b", 2)).toDF("k", "v")

    val out = CacheTransform().apply(in)

    assertEquals(in.collect().toSet, out.collect().toSet)
    out.unpersist()
  }
}
