package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.SparkTest
import com.kakao.actionbase.pipeline.workflow.StepSpec
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class StepsBuilderTest extends SparkTest {

  private val ShowSinkFqn      = "com.kakao.actionbase.pipeline.steps.sink.ShowSink"
  private val SampleSourceFqn  = "com.kakao.actionbase.pipeline.steps.source.SampleSource"
  private val SqlTransformFqn  = "com.kakao.actionbase.pipeline.steps.transform.SqlTransform"

  @Test
  def buildsAndRunsSourceSinkChain(): Unit = {
    val steps = Seq(
      StepSpec(SampleSourceFqn, Map("n" -> 50L, "columns" -> Seq("a", "b"))),
      StepSpec(ShowSinkFqn)
    )

    StepsBuilder.build(steps).run()
  }

  @Test
  def buildsAndRunsSourceTransformSinkChain(): Unit = {
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
    val ex    = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(steps)
    )
    assertTrue(ex.getMessage.contains("at least one Sink"), ex.getMessage)
  }

  @Test
  def runsMultipleSinksOverSharedUpstream(): Unit = {
    val FileSinkFqn = "com.kakao.actionbase.pipeline.steps.sink.FileSink"
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
    val FileSinkFqn = "com.kakao.actionbase.pipeline.steps.sink.FileSink"
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
}
