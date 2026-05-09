package com.kakao.actionbase.pipeline.runner

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class ClassResolverTest {

  @Test
  def resolvesFullyQualifiedName(): Unit = {
    val cls = ClassResolver.resolve(
      "com.kakao.actionbase.pipeline.steps.source.SampleSource",
      ClassResolver.StepRoots
    )
    assertEquals("com.kakao.actionbase.pipeline.steps.source.SampleSource", cls.getName)
  }

  @Test
  def resolvesShortNameUnderJobRoot(): Unit = {
    val cls = ClassResolver.resolve("SparkPiJob$", ClassResolver.JobRoots)
    assertEquals("com.kakao.actionbase.pipeline.jobs.SparkPiJob$", cls.getName)
  }

  @Test
  def resolvesShortNameAcrossStepRoots(): Unit = {
    // SampleSource lives under steps.source — the resolver must try each root in order
    val source = ClassResolver.resolve("SampleSource", ClassResolver.StepRoots)
    assertEquals("com.kakao.actionbase.pipeline.steps.source.SampleSource", source.getName)

    // SqlTransform under steps.transform
    val transform = ClassResolver.resolve("SqlTransform", ClassResolver.StepRoots)
    assertEquals("com.kakao.actionbase.pipeline.steps.transform.SqlTransform", transform.getName)

    // ShowSink under steps.sink
    val sink = ClassResolver.resolve("ShowSink", ClassResolver.StepRoots)
    assertEquals("com.kakao.actionbase.pipeline.steps.sink.ShowSink", sink.getName)
  }

  @Test
  def throwsWhenUnresolvable(): Unit = {
    val ex = assertThrows(
      classOf[ClassNotFoundException],
      () => ClassResolver.resolve("DoesNotExist", ClassResolver.StepRoots)
    )
    assertTrue(ex.getMessage.contains("DoesNotExist"), ex.getMessage)
  }
}
