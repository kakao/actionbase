package com.kakao.actionbase.pipeline.jobs

import com.kakao.actionbase.pipeline.dsl._
import com.kakao.actionbase.pipeline.steps.sink.ShowSink
import com.kakao.actionbase.pipeline.steps.source.SampleSource
import com.kakao.actionbase.pipeline.steps.transform.SqlTransform

/** Worked example of the Source ~> Transform ~> Sink DSL: Monte-Carlo estimation of π.
  *
  * Throws `samples` random points into the unit square, computes π ≈ 4 · inside / total via SQL, and prints the
  * single-row result to stdout. Pure composition of reusable Steps — no Job-specific Step definitions.
  */
case class SparkPiCfg(samples: Long = 1000000L)

object SparkPiJob extends Job[SparkPiCfg] {

  override def plan(cfg: SparkPiCfg): Plan.Closed =
    SampleSource(cfg.samples, Seq("x", "y")).as("samples") ~>
      SqlTransform(
        "SELECT 4.0 * SUM(CASE WHEN x*x + y*y <= 1 THEN 1 ELSE 0 END) / COUNT(*) AS pi FROM samples"
      ) ~>
      ShowSink()
}
