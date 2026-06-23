package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.workflow.StepSpec
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class ClassResolverTest {

  @Test
  def resolvesShortNameUnderRoots(): Unit = {
    val cls = ClassResolver.resolve("ShowSink", ClassResolver.StepRoots)
    assertEquals("com.kakao.actionbase.pipeline.steps.sink.ShowSink", cls.getName)
  }

  @Test
  def resolvesFullyQualifiedName(): Unit = {
    val cls = ClassResolver.resolve(
      "com.kakao.actionbase.pipeline.steps.source.SampleSource",
      ClassResolver.StepRoots
    )
    assertEquals("com.kakao.actionbase.pipeline.steps.source.SampleSource", cls.getName)
  }

  @Test
  def throwsOnUnresolvableName(): Unit = {
    assertThrows(
      classOf[ClassNotFoundException],
      () => ClassResolver.resolve("DoesNotExist", ClassResolver.StepRoots)
    )
  }

  @Test
  def rejectsAmbiguousSimpleName(): Unit = {
    // Two steps.* subpackages declare a `CollidingName`; resolving the bare name must refuse rather than pick one.
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => ClassResolver.resolve("CollidingName", ClassResolver.StepRoots)
    )
    assertTrue(ex.getMessage.contains("ambiguous"), ex.getMessage)
    assertTrue(ex.getMessage.contains("steps.source.CollidingName"), ex.getMessage)
    assertTrue(ex.getMessage.contains("steps.sink.CollidingName"), ex.getMessage)
  }

  @Test
  def loadsClassWithoutTriggeringStaticInitializer(): Unit = {
    // The sentinel's <clinit> throws. If `resolve` initializes the class, load fails. If it merely loads (per the
    // `initialize = false` contract), load succeeds and we keep the un-initialized class reference.
    val cls = ClassResolver.resolve(
      "com.kakao.actionbase.pipeline.runner.ThrowingClinitSentinel",
      Seq.empty
    )
    assertEquals("com.kakao.actionbase.pipeline.runner.ThrowingClinitSentinel", cls.getName)
  }

  @Test
  def stepsBuilderRejectsNonStepFqnBeforeInit(): Unit = {
    // End-to-end guard: a YAML naming a non-Step class must fail at the type-check, not at `convertValue` or later. The
    // sentinel's <clinit> throws — if StepsBuilder triggered init before the type check, we'd see ExceptionInInitializer
    // instead of the expected `not a Step` IllegalArgumentException.
    val spec = StepSpec("com.kakao.actionbase.pipeline.runner.ThrowingClinitSentinel")
    val ex = assertThrows(
      classOf[IllegalArgumentException],
      () => StepsBuilder.build(Seq(spec))
    )
    assertTrue(ex.getMessage.contains("not a Step"), ex.getMessage)
  }
}
