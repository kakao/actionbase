package com.kakao.actionbase.pipeline.dsl

import com.kakao.actionbase.pipeline.SparkTest
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

/**
  * Tests for the Split primitive: port selection, plan-time port validation, single-execution port memoization, and
  * `Plan.bundle` composition of per-port Sinks.
  */
class PlanSplitTest extends SparkTest {

  /** A Source that emits `(k: int, v: int)` rows we can route by `k`. */
  private class KvSource(rows: Seq[(Int, Int)]) extends Source {
    override def read()(implicit spark: SparkSession): DataFrame = {
      import spark.implicits._
      rows.toDF("k", "v")
    }
  }

  /**
    * A Split that partitions input rows by even/odd `k`. The number of `split()` invocations is observable so tests can
    * assert the port memo runs the body exactly once per Plan execution.
    */
  private class ParitySplit extends Split {
    @volatile var splitCalls: Int   = 0
    override def ports: Seq[String] = Seq("even", "odd")
    override def split(in: DataFrame)(implicit spark: SparkSession): Map[String, DataFrame] = {
      splitCalls += 1
      Map(
        "even" -> in.where("k % 2 = 0"),
        "odd"  -> in.where("k % 2 = 1")
      )
    }
  }

  private class CapturingSink extends Sink {
    @volatile var received: DataFrame                                     = _
    override def write(in: DataFrame)(implicit spark: SparkSession): Unit = received = in
  }

  @Test
  def routesPortsToTheirRespectiveSinks(): Unit = {
    val src      = new KvSource(Seq((0, 10), (1, 11), (2, 12), (3, 13)))
    val splitter = new ParitySplit
    val evenSink = new CapturingSink
    val oddSink  = new CapturingSink

    val forked: Plan.Forked = (src: Plan.Open) ~> splitter
    Plan
      .bundle(
        forked("even") ~> evenSink,
        forked("odd") ~> oddSink
      )
      .run()

    assertEquals(Set(0 -> 10, 2 -> 12), evenSink.received.collect().map(r => r.getInt(0) -> r.getInt(1)).toSet)
    assertEquals(Set(1 -> 11, 3 -> 13), oddSink.received.collect().map(r => r.getInt(0) -> r.getInt(1)).toSet)
  }

  @Test
  def splitBodyRunsOnceAcrossMultiplePortConsumers(): Unit = {
    val src      = new KvSource(Seq((0, 10), (1, 11)))
    val splitter = new ParitySplit
    val evenSink = new CapturingSink
    val oddSink  = new CapturingSink

    Plan
      .bundle(
        ((src: Plan.Open) ~> splitter)("even") ~> evenSink,
        ((src: Plan.Open) ~> splitter)("odd") ~> oddSink
      )
      .run()

    // Two independently-constructed `~> splitter` expressions are *different* AST nodes (different Ast.Sp identities),
    // so each runs its split body once. Identity-based memo: not deduped structurally. Two calls total.
    assertEquals(2, splitter.splitCalls, "AST identity guards against accidental structural dedup")
  }

  @Test
  def splitBodyRunsOnceWhenForkedReused(): Unit = {
    val src      = new KvSource(Seq((0, 10), (1, 11)))
    val splitter = new ParitySplit
    val evenSink = new CapturingSink
    val oddSink  = new CapturingSink

    val forked = (src: Plan.Open) ~> splitter
    Plan
      .bundle(
        forked("even") ~> evenSink,
        forked("odd") ~> oddSink
      )
      .run()

    assertEquals(1, splitter.splitCalls, "shared Forked: split body runs once across both port consumers")
  }

  @Test
  def rejectsUnknownPortAtPlanTime(): Unit = {
    val src      = new KvSource(Seq.empty)
    val splitter = new ParitySplit
    val forked   = (src: Plan.Open) ~> splitter

    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => forked("ghost"): Plan.Open
    )
    assertTrue(ex.getMessage.contains("ghost"), ex.getMessage)
  }

  @Test
  def forkedExposesPortsList(): Unit = {
    val src      = new KvSource(Seq.empty)
    val splitter = new ParitySplit
    val forked   = (src: Plan.Open) ~> splitter
    assertEquals(Seq("even", "odd"), forked.ports)
  }

  @Test
  def bundleOfSinglePassesThrough(): Unit = {
    val src     = new KvSource(Seq((0, 10)))
    val sink    = new CapturingSink
    val one     = (src: Plan.Open) ~> sink
    val bundled = Plan.bundle(one)
    bundled.run()
    assertNotNull(sink.received)
  }

  @Test
  def bundleRequiresAtLeastOneClosed(): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => Plan.bundle(): Plan.Closed
    )
  }
}
