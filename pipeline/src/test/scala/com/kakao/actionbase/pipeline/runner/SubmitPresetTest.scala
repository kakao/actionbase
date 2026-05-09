package com.kakao.actionbase.pipeline.runner

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class SubmitPresetTest {

  private val Presets = Map(
    "small" -> Map[String, Any](
      "executor-memory" -> "1g",
      "executor-cores"  -> 1,
      "driver-memory"   -> "512m",
      "conf" -> Map[String, Any](
        "spark.sql.shuffle.partitions"     -> 4,
        "spark.dynamicAllocation.enabled"  -> "true"
      )
    )
  )

  @Test
  def passesSubmitThroughWhenNoPresetReferenced(): Unit = {
    val submit = Map[String, Any]("executor-memory" -> "2g")
    assertEquals(submit, SubmitPreset.resolve(submit, Presets))
  }

  @Test
  def expandsPresetWhenReferenced(): Unit = {
    val submit = Map[String, Any]("preset" -> "small")
    val result = SubmitPreset.resolve(submit, Presets)
    assertEquals("1g", result("executor-memory"))
    assertEquals(1, result("executor-cores"))
    assertFalse(result.contains("preset"), "the `preset` key itself should be stripped")
  }

  @Test
  def topLevelOverrideReplacesPresetValue(): Unit = {
    val submit = Map[String, Any]("preset" -> "small", "driver-memory" -> "1g")
    val result = SubmitPreset.resolve(submit, Presets)
    assertEquals("1g", result("driver-memory"))                  // user override
    assertEquals("1g", result("executor-memory"))                // preset preserved
  }

  @Test
  def nestedMapDeepMerges(): Unit = {
    val submit = Map[String, Any](
      "preset" -> "small",
      "conf"   -> Map("spark.sql.shuffle.partitions" -> 8)        // override one conf entry
    )
    val result = SubmitPreset.resolve(submit, Presets)
    val conf   = result("conf").asInstanceOf[Map[String, Any]]
    assertEquals(8, conf("spark.sql.shuffle.partitions"))           // user wins
    assertEquals("true", conf("spark.dynamicAllocation.enabled"))   // preset preserved
  }

  @Test
  def throwsOnUnknownPreset(): Unit = {
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => SubmitPreset.resolve(Map("preset" -> "huge"), Presets)
    )
    assertTrue(ex.getMessage.contains("huge"), ex.getMessage)
  }
}
