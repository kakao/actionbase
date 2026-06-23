package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.SparkTest
import com.kakao.actionbase.pipeline.workflow.StepSpec
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class StepsBuilderTest extends SparkTest {

  private val ShowSinkFqn     = "com.kakao.actionbase.pipeline.steps.sink.ShowSink"
  private val FileSinkFqn     = "com.kakao.actionbase.pipeline.steps.sink.FileSink"
  private val SampleSourceFqn = "com.kakao.actionbase.pipeline.steps.source.SampleSource"
  private val SqlTransformFqn = "com.kakao.actionbase.pipeline.steps.transform.SqlTransform"
  private val ParitySplitFqn  = "com.kakao.actionbase.pipeline.steps.split.ParitySplit"

  @Test
  def buildsAndRunsSourceSinkChain(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 50L, "columns" -> Seq("a", "b"))),
      StepSpec(ShowSinkFqn)
    )

    StepsBuilder.build(steps).run()
  }

  @Test
  def buildsAndRunsSourceMergeSinkChain(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 20L, "columns" -> Seq("x"))),
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT x * 2 AS y FROM _0")),
      StepSpec(ShowSinkFqn)
    )

    StepsBuilder.build(steps).run()
  }

  @Test
  def buildsAndRunsJoinAcrossLabeledSources(): Unit = {
    val steps = Seq(
      StepSpec(
        SampleSourceFqn,
        args = Map("n" -> 5L, "columns" -> Seq("u")),
        as = Some("users")
      ),
      StepSpec(
        SampleSourceFqn,
        args = Map("n" -> 5L, "columns" -> Seq("e")),
        as = Some("events")
      ),
      StepSpec(
        SqlTransformFqn,
        args = Map("query" -> "SELECT u.u, e.e FROM users u CROSS JOIN events e"),
        inputs = Seq("users", "events")
      ),
      StepSpec(ShowSinkFqn)
    )

    StepsBuilder.build(steps).run()
  }

  @Test
  def rejectsSinkWithoutUpstream(): Unit = {
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(Seq(StepSpec(ShowSinkFqn)))
    )
    assertTrue(ex.getMessage.contains("no upstream"), ex.getMessage)
  }

  @Test
  def rejectsWorkflowWithNoSink(): Unit = {
    val steps = Seq(StepSpec(SampleSourceFqn, Map("n" -> 5L, "columns" -> Seq("a"))))
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("at least one Sink"), ex.getMessage)
  }

  @Test
  def runsMultipleSinksOverSharedUpstream(): Unit = {
    val tmpA = java.nio.file.Files.createTempDirectory("multi-sink-a-").resolve("out").toString
    val tmpB = java.nio.file.Files.createTempDirectory("multi-sink-b-").resolve("out").toString

    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 5L, "columns" -> Seq("x")), as = Some("data")),
      StepSpec(FileSinkFqn, Map("path" -> tmpA, "format" -> "parquet"), inputs = Seq("data")),
      StepSpec(FileSinkFqn, Map("path" -> tmpB, "format" -> "parquet"), inputs = Seq("data"))
    )

    StepsBuilder.build(steps).run()

    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpA)), s"sink A should write: $tmpA")
    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpB)), s"sink B should write: $tmpB")
  }

  @Test
  def runsIndependentSinkSubTrees(): Unit = {
    val tmpA = java.nio.file.Files.createTempDirectory("indep-a-").resolve("out").toString
    val tmpB = java.nio.file.Files.createTempDirectory("indep-b-").resolve("out").toString

    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 3L, "columns" -> Seq("a"))),
      StepSpec(FileSinkFqn, Map("path" -> tmpA, "format" -> "parquet")),
      StepSpec(SampleSourceFqn, Map("n" -> 3L, "columns" -> Seq("b"))),
      StepSpec(FileSinkFqn, Map("path" -> tmpB, "format" -> "parquet"))
    )

    StepsBuilder.build(steps).run()

    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpA)), s"first subtree should write: $tmpA")
    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpB)), s"second subtree should write: $tmpB")
  }

  @Test
  def rejectsUnknownInputLabel(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 5L, "columns" -> Seq("a"))),
      StepSpec(ShowSinkFqn, inputs = Seq("ghost"))
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("ghost"), ex.getMessage)
  }

  @Test
  def rejectsDuplicateLabel(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 5L, "columns" -> Seq("a")), as = Some("dup")),
      StepSpec(SampleSourceFqn, Map("n" -> 5L, "columns" -> Seq("b")), as = Some("dup")),
      StepSpec(ShowSinkFqn)
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("duplicate"), ex.getMessage)
  }

  @Test
  def buildsAndRunsSplitChain(): Unit = {
    // Source → SqlTransform (project k from x) → ParitySplit → two FileSinks via port labels.
    val tmpEven = java.nio.file.Files.createTempDirectory("split-even-").resolve("out").toString
    val tmpOdd  = java.nio.file.Files.createTempDirectory("split-odd-").resolve("out").toString

    // YAML-equivalent representation; `as: {even: ..., odd: ...}` is a Java map at the StepSpec level.
    val portLabels = new java.util.LinkedHashMap[String, String]()
    portLabels.put("even", "evenRows")
    portLabels.put("odd", "oddRows")

    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 6L, "columns" -> Seq("v"))),
      // Synthesize a 0..5 `k` column so ParitySplit has something to route on.
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0")),
      StepSpec(ParitySplitFqn, as = Some(portLabels)),
      StepSpec(
        FileSinkFqn,
        Map("path" -> tmpEven, "format" -> "parquet", "mode" -> "overwrite"),
        inputs = Seq("evenRows")
      ),
      StepSpec(
        FileSinkFqn,
        Map("path" -> tmpOdd, "format" -> "parquet", "mode" -> "overwrite"),
        inputs = Seq("oddRows")
      )
    )

    StepsBuilder.build(steps).run()

    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpEven)))
    assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get(tmpOdd)))

    val even = spark.read.parquet(tmpEven).collect().map(_.getInt(0)).toSet
    val odd  = spark.read.parquet(tmpOdd).collect().map(_.getInt(0)).toSet
    assertTrue(even.forall(_ % 2 == 0), s"even partition leaked odd rows: $even")
    assertTrue(odd.forall(_ % 2 == 1), s"odd partition leaked even rows: $odd")
  }

  @Test
  def rejectsStringAsOnSplit(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 4L, "columns" -> Seq("v"))),
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0")),
      StepSpec(ParitySplitFqn, as = Some("oneLabel")), // wrong shape for a Split
      StepSpec(ShowSinkFqn, inputs = Seq("oneLabel"))
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("map form"), ex.getMessage)
  }

  @Test
  def rejectsUnknownPortInLabels(): Unit = {
    val ghostPorts = new java.util.LinkedHashMap[String, String]()
    ghostPorts.put("ghost", "x")
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 4L, "columns" -> Seq("v"))),
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0")),
      StepSpec(ParitySplitFqn, as = Some(ghostPorts)),
      StepSpec(ShowSinkFqn, inputs = Seq("x"))
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("ghost"), ex.getMessage)
  }

  @Test
  def rejectsSplitWithUnlabeledPort(): Unit = {
    // Only `even` is mapped; `odd` would be silently dropped, so the build must reject it.
    val portLabels = new java.util.LinkedHashMap[String, String]()
    portLabels.put("even", "evenRows")
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 4L, "columns" -> Seq("v"))),
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0")),
      StepSpec(ParitySplitFqn, as = Some(portLabels)),
      StepSpec(ShowSinkFqn, inputs = Seq("evenRows"))
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("unlabeled"), ex.getMessage)
    assertTrue(ex.getMessage.contains("odd"), ex.getMessage)
  }

  @Test
  def rejectsLinearChainAfterSplit(): Unit = {
    // After a Split, the linear-chain default upstream is dropped — next step must reference a port label explicitly.
    val portLabels = new java.util.LinkedHashMap[String, String]()
    portLabels.put("even", "evenRows")
    portLabels.put("odd", "oddRows")
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 4L, "columns" -> Seq("v"))),
      StepSpec(SqlTransformFqn, Map("query" -> "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0")),
      StepSpec(ParitySplitFqn, as = Some(portLabels)),
      StepSpec(ShowSinkFqn) // no `inputs:` and no `prev` after Split
    )
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("no upstream"), ex.getMessage)
  }
}
