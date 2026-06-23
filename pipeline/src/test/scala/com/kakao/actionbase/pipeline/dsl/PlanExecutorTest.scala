package com.kakao.actionbase.pipeline.dsl

import com.kakao.actionbase.pipeline.SparkTest
import com.kakao.actionbase.pipeline.steps.transform.SqlTransform
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.storage.StorageLevel
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

/**
  * Executor-level tests: fanOut caching and AST-identity memoization. These exercise behavior the YAML-facing
  * `StepsBuilderTest` cannot verify directly (no read-call counter on built-in Sources).
  */
class PlanExecutorTest extends SparkTest {

  /** Source that counts how many times `read()` is invoked, so tests can assert single-materialization. */
  private class CountingSource(rows: Long) extends Source {
    @volatile var calls: Int = 0
    override def read()(implicit spark: SparkSession): DataFrame = {
      calls += 1
      spark.range(rows).toDF("n")
    }
  }

  /** Sink that captures the DataFrame it receives and its storage level at write time. */
  private class CapturingSink extends Sink {
    @volatile var received: DataFrame          = _
    @volatile var storageAtWrite: StorageLevel = StorageLevel.NONE
    override def write(in: DataFrame)(implicit spark: SparkSession): Unit = {
      storageAtWrite = in.storageLevel
      received = in
    }
  }

  @Test
  def fanOutCachesUpstreamAndUnpersistsAfter(): Unit = {
    val src   = new CountingSource(5L)
    val sinkA = new CapturingSink
    val sinkB = new CapturingSink

    src.fanOut(_ ~> sinkA, _ ~> sinkB).run()

    assertEquals(1, src.calls, "source must be read only once across fanOut branches")
    assertNotEquals(
      StorageLevel.NONE,
      sinkA.storageAtWrite,
      "branch sink sees the cached upstream"
    )
    assertNotEquals(
      StorageLevel.NONE,
      sinkB.storageAtWrite,
      "branch sink sees the cached upstream"
    )
    assertEquals(
      StorageLevel.NONE,
      sinkA.received.storageLevel,
      "cached upstream is unpersisted after run"
    )
  }

  @Test
  def fanOutBranchSupportsFlowAndMerge(): Unit = {
    val src   = new CountingSource(4L)
    val sinkA = new CapturingSink
    val sinkB = new CapturingSink

    src
      .fanOut(
        _ ~> sinkA,
        _ ~> SqlTransform("SELECT n * 2 AS n FROM _0") ~> sinkB
      )
      .run()

    assertEquals(Set(0L, 1L, 2L, 3L), sinkA.received.collect().map(_.getLong(0)).toSet)
    assertEquals(Set(0L, 2L, 4L, 6L), sinkB.received.collect().map(_.getLong(0)).toSet)
  }

  @Test
  def fanOutRequiresAtLeastOneBranch(): Unit = {
    val src = new CountingSource(1L)
    assertThrows(
      classOf[IllegalArgumentException],
      () => src.fanOut(): Plan.Closed
    )
  }

  @Test
  def fanOutRejectsBranchIntroducingNewSource(): Unit = {
    // The branch ignores the supplied fork-root Open and chains a fresh Source instead — this defeats the shared-
    // upstream contract and must be caught at plan construction, not at run().
    val src   = new CountingSource(1L)
    val other = new CountingSource(1L)
    val sink  = new CapturingSink
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => src.fanOut(_ => (other: Plan.Open) ~> sink)
    )
    assertTrue(ex.getMessage.contains("Source"), ex.getMessage)
  }

  @Test
  def fanOutRejectsBranchJoiningNewSource(): Unit = {
    // `+`-combining the fork root with another Source compiles but breaks the "branches consume shared upstream" rule.
    val src   = new CountingSource(1L)
    val other = new CountingSource(1L)
    val sink  = new CapturingSink
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => src.fanOut(open => (open + (other: Plan.Open).as("x")) ~> SqlTransform("SELECT n FROM _0") ~> sink)
    )
    assertTrue(ex.getMessage.contains("Source"), ex.getMessage)
  }

  @Test
  def memoizationSharesUpstreamAcrossSinks(): Unit = {
    val src    = new CountingSource(3L)
    val sinkA  = new CapturingSink
    val sinkB  = new CapturingSink
    val srcAst = Ast.Src(src)
    val plan = Plan.closed(
      Ast.Group(Seq(Ast.Snk(srcAst, sinkA), Ast.Snk(srcAst, sinkB)))
    )

    plan.run()

    assertEquals(1, src.calls, "shared upstream materializes exactly once across multiple sinks")
  }

  @Test
  def memoizationKeysOnAstIdentityNotEquality(): Unit = {
    // Two separately-constructed Sources with identical args are distinct AST nodes — memo must not dedupe them.
    val srcA  = new CountingSource(2L)
    val srcB  = new CountingSource(2L)
    val sinkA = new CapturingSink
    val sinkB = new CapturingSink
    val plan = Plan.closed(
      Ast.Group(Seq(Ast.Snk(Ast.Src(srcA), sinkA), Ast.Snk(Ast.Src(srcB), sinkB)))
    )

    plan.run()

    assertEquals(1, srcA.calls)
    assertEquals(1, srcB.calls)
  }
}
