package com.kakao.actionbase.pipeline.dsl

import com.kakao.actionbase.pipeline.SparkTest
import com.kakao.actionbase.pipeline.steps.sink.ShowSink
import com.kakao.actionbase.pipeline.steps.source.SampleSource
import com.kakao.actionbase.pipeline.steps.transform.SqlTransform
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class PlanMultiInputTest extends SparkTest {

  /** Captures the joined DataFrame so we can assert. Test-local sink — multi-input not needed. */
  private case class CaptureSink() extends Sink {
    @volatile var captured: DataFrame = _
    override def write(in: DataFrame)(implicit spark: SparkSession): Unit = captured = in
  }

  @Test
  def combinesTwoLabeledSourcesViaPlus(): Unit = {
    val sink = CaptureSink()

    val plan: Plan.Closed =
      (SampleSource(3, Seq("u")).as("users") + SampleSource(2, Seq("e")).as("events")) ~>
        SqlTransform("SELECT u, e FROM users CROSS JOIN events") ~>
        sink

    plan.run()(spark)

    val rows = sink.captured.collect()
    assertEquals(6, rows.length, "3 users × 2 events = 6 rows")
    assertEquals(Set("u", "e"), sink.captured.columns.toSet)
  }

  @Test
  def transformOutputsAreOpenSoAsAndPlusApply(): Unit = {
    // Each `~> Transform` returns Plan.Open, so `.as(...)` and `+` work on Transform outputs the same as on Sources.
    val sink = CaptureSink()

    val left  = SampleSource(2, Seq("u")) ~> SqlTransform("SELECT u AS x FROM _0")
    val right = SampleSource(2, Seq("e")) ~> SqlTransform("SELECT e AS y FROM _0")

    val plan: Plan.Closed =
      (left.as("L") + right.as("R")) ~>
        SqlTransform("SELECT L.x, R.y FROM L CROSS JOIN R") ~>
        sink

    plan.run()(spark)
    assertEquals(4, sink.captured.collect().length, "2 × 2 = 4 rows")
    assertEquals(Set("x", "y"), sink.captured.columns.toSet)
  }

  @Test
  def chainsThreePlusInputs(): Unit = {
    val sink = CaptureSink()

    val plan: Plan.Closed =
      (SampleSource(2, Seq("a")).as("a") +
        SampleSource(2, Seq("b")).as("b") +
        SampleSource(2, Seq("c")).as("c")) ~>
        SqlTransform("SELECT a.a, b.b, c.c FROM a, b, c") ~>
        sink

    plan.run()(spark)
    assertEquals(8, sink.captured.collect().length, "2 × 2 × 2 = 8 rows")
  }
}
