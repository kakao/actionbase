package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.runner.Expression.Context
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.collection.JavaConverters._

class ExtendsResolverTest {

  @Test
  def deepMergesExtendedWithSiblings(): Unit = {
    val preset = Map[String, Any]("driver-memory" -> "1g", "executor-memory" -> "2g").asJava
    val ctx    = Context(presets = Map("small" -> preset))

    val tree = Map[String, Any](
      "$extends" -> "${{ presets.small }}",
      "conf"     -> Map[String, Any]("spark.shuffle.partitions" -> 8).asJava
    ).asJava

    val out = ExtendsResolver.resolve(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals("1g", out("driver-memory"))
    assertEquals("2g", out("executor-memory"))
    assertEquals(8, out("conf").asInstanceOf[java.util.Map[String, Any]].get("spark.shuffle.partitions"))
  }

  @Test
  def siblingOverridesExtendedAtSameKey(): Unit = {
    val preset = Map[String, Any]("driver-memory" -> "1g").asJava
    val ctx    = Context(presets = Map("small" -> preset))

    val tree = Map[String, Any](
      "$extends"      -> "${{ presets.small }}",
      "driver-memory" -> "4g"
    ).asJava

    val out = ExtendsResolver.resolve(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals("4g", out("driver-memory"))
  }

  @Test
  def deepMergesNestedMaps(): Unit = {
    val preset = Map[String, Any](
      "conf" -> Map[String, Any]("a" -> 1, "b" -> 2).asJava
    ).asJava
    val ctx = Context(presets = Map("p" -> preset))

    val tree = Map[String, Any](
      "$extends" -> "${{ presets.p }}",
      "conf"     -> Map[String, Any]("b" -> 99, "c" -> 3).asJava
    ).asJava

    val out  = ExtendsResolver.resolve(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    val conf = out("conf").asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals(1, conf("a"))
    assertEquals(99, conf("b"))
    assertEquals(3, conf("c"))
  }

  @Test
  def resolvesNestedExtendsInsideExtended(): Unit = {
    val inner = Map[String, Any]("driver-memory" -> "1g").asJava
    val outer = Map[String, Any](
      "$extends"        -> "${{ presets.inner }}",
      "executor-memory" -> "2g"
    ).asJava
    val ctx = Context(presets = Map("inner" -> inner, "outer" -> outer))

    val tree = Map[String, Any]("$extends" -> "${{ presets.outer }}").asJava

    val out = ExtendsResolver.resolve(tree, ctx).asInstanceOf[java.util.Map[String, Any]].asScala
    assertEquals("1g", out("driver-memory"))
    assertEquals("2g", out("executor-memory"))
  }

  @Test
  def rejectsNonStringExtendsValue(): Unit = {
    val ctx  = Context()
    val tree = Map[String, Any]("$extends" -> 42).asJava
    assertThrows(classOf[IllegalArgumentException], () => ExtendsResolver.resolve(tree, ctx))
  }

  @Test
  def rejectsExtendsValueNotWrappedInExpression(): Unit = {
    val ctx  = Context()
    val tree = Map[String, Any]("$extends" -> "presets.small").asJava
    assertThrows(classOf[IllegalArgumentException], () => ExtendsResolver.resolve(tree, ctx))
  }

  @Test
  def rejectsExtendsValueResolvingToScalar(): Unit = {
    val ctx  = Context(env = Map("x" -> "scalar"))
    val tree = Map[String, Any]("$extends" -> "${{ env.x }}").asJava
    assertThrows(classOf[IllegalArgumentException], () => ExtendsResolver.resolve(tree, ctx))
  }
}
