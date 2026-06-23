package com.kakao.actionbase.pipeline.dsl

import com.kakao.actionbase.pipeline.steps.sink.ShowSink
import com.kakao.actionbase.pipeline.steps.source.SampleSource
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

/**
  * Direct unit coverage for `Job.parseArgv` and `Job.planFromMap` — the Cfg-binding seam between argv/YAML and a
  * subclass's `plan(cfg)`. The `main` entry point itself needs SparkSession lifecycle and is exercised end-to-end via
  * `StepsBuilderTest`.
  */
class JobTest {

  @Test
  def parseArgvHandlesKeyValuePairs(): Unit = {
    val parsed = Job.parseArgv(Array("--in=path/to/file", "--n=10"))
    assertEquals(Map("in" -> "path/to/file", "n" -> "10"), parsed)
  }

  @Test
  def parseArgvSplitsOnFirstEqualsOnly(): Unit = {
    // Values may legitimately contain `=` (e.g., SQL `WHERE k = 'v'`); only the first `=` separates key from value.
    val parsed = Job.parseArgv(Array("--query=SELECT * FROM t WHERE k='v=1'"))
    assertEquals(Map("query" -> "SELECT * FROM t WHERE k='v=1'"), parsed)
  }

  @Test
  def parseArgvIgnoresUnrecognizedTokens(): Unit = {
    // Non-`--` tokens and flags without `=` are dropped (with a stderr warning the test doesn't capture). The returned
    // map must only contain well-formed pairs so downstream Cfg binding is predictable.
    val parsed = Job.parseArgv(Array("positional", "--bare-flag", "--in=x"))
    assertEquals(Map("in" -> "x"), parsed)
  }

  @Test
  def planFromMapBindsNestedCfg(): Unit = {
    val plan = JobTest.TestJob.planFromMap(Map("in" -> "p", "n" -> 5, "flag" -> true))
    assertNotNull(plan, "planFromMap must return a runnable Plan.Closed")
  }

  @Test
  def planFromMapFailsOnMissingRequiredPrimitive(): Unit = {
    // `n: Int` has no default — its absence must throw rather than silently become 0.
    assertThrows(
      classOf[RuntimeException],
      () => JobTest.TestJob.planFromMap(Map("in" -> "p"))
    )
  }

  @Test
  def planFromMapToleratesUnknownTopLevelKeys(): Unit = {
    // Top-level Cfg uses the lax mapper so Spark's own `--spark.*` flags don't break job startup.
    val plan = JobTest.TestJob.planFromMap(Map("in" -> "p", "n" -> 3, "spark.master" -> "local[*]"))
    assertNotNull(plan)
  }
}

object JobTest {
  // Top-level case class avoids an outer-`this` reference that confuses Jackson's Scala module.
  case class TestCfg(in: String, n: Int, flag: Boolean = false)

  object TestJob extends Job[TestCfg] {
    override def plan(cfg: TestCfg): Plan.Closed =
      Plan.closed(Ast.Snk(Ast.Src(SampleSource(cfg.n.toLong, Seq("v"))), ShowSink()))
  }
}
