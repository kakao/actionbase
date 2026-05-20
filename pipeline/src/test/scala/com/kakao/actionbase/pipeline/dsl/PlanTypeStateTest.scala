package com.kakao.actionbase.pipeline.dsl

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

/**
  * Encodes the type-state guarantee that a chain without a Sink is not runnable — `Plan.Open` does not expose `run()`,
  * `Plan.Closed` does. Compile-time enforcement is the actual guarantee; this is a regression sentinel that fires if
  * `run` ever leaks onto the wrong type.
  */
class PlanTypeStateTest {

  @Test
  def planOpenDoesNotExposeRun(): Unit = {
    assertFalse(
      classOf[Plan.Open].getMethods.exists(_.getName == "run"),
      "Plan.Open must not expose run() — a chain without a Sink is not runnable"
    )
  }

  @Test
  def planClosedExposesRun(): Unit = {
    assertTrue(
      classOf[Plan.Closed].getMethods.exists(_.getName == "run"),
      "Plan.Closed must expose run() — a chain ending in a Sink is runnable"
    )
  }

  @Test
  def multiOpenDoesNotExposeRun(): Unit = {
    // A combined multi-input chain (a + b) must still terminate via a Merge and then a Sink before it can run.
    assertFalse(
      classOf[Plan.MultiOpen].getMethods.exists(_.getName == "run"),
      "Plan.MultiOpen must not expose run()"
    )
  }
}
