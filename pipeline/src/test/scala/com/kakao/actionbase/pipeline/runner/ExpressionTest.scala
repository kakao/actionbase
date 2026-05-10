package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.runner.Expression.{Context, NeedsView}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.collection.JavaConverters._

class ExpressionTest {

  @Test
  def evaluatesEnv(): Unit = {
    val ctx = Context(env = Map("samples" -> "1000"))
    assertEquals("1000", Expression.evaluate("env.samples", ctx))
  }

  @Test
  def evaluatesPresetReturningMap(): Unit = {
    val preset = Map[String, Any]("driver-memory" -> "1g", "executor-memory" -> "2g")
    val ctx    = Context(presets = Map("spark-small" -> preset))
    assertEquals(preset, Expression.evaluate("presets.spark-small", ctx))
  }

  @Test
  def evaluatesNeedsResultAndOutputs(): Unit = {
    val ctx = Context(needs = Map("pi" -> NeedsView("success", Map("estimate" -> "3.14"))))
    assertEquals("success", Expression.evaluate("needs.pi.result", ctx))
    assertEquals("3.14", Expression.evaluate("needs.pi.outputs.estimate", ctx))
  }

  @Test
  def loadCallInvokesContextLoader(): Unit = {
    val loaded = Map[String, Any]("a" -> 1).asJava
    val ctx    = Context(loadYaml = path => { assertEquals("preset.yaml", path); loaded })
    assertEquals(loaded, Expression.evaluate("load('preset.yaml')", ctx))
  }

  @Test
  def resolveDeepReplacesWholeTokenWithRawValue(): Unit = {
    val preset = Map[String, Any]("driver-memory" -> "1g").asJava
    val ctx    = Context(presets = Map("small" -> preset))
    val tree   = Map("submit" -> "${{ presets.small }}").asJava

    val out = Expression.resolveDeep(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals(preset, out("submit"))
  }

  @Test
  def resolveDeepDoesStringInterpolation(): Unit = {
    val ctx  = Context(env = Map("date" -> "2026-05-10"))
    val tree = Map("path" -> "/data/${{ env.date }}/file").asJava
    val out  = Expression.resolveDeep(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals("/data/2026-05-10/file", out("path"))
  }

  @Test
  def rejectsUnknownEnvKey(): Unit = {
    val ctx = Context()
    assertThrows(classOf[NoSuchElementException], () => Expression.evaluate("env.missing", ctx))
  }

  @Test
  def rejectsMalformedExpression(): Unit = {
    val ctx = Context()
    assertThrows(classOf[IllegalArgumentException], () => Expression.evaluate("not.a.known.prefix", ctx))
  }
}
