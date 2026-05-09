package com.kakao.actionbase.pipeline.jobs

import com.kakao.actionbase.pipeline.WorkflowFixtures
import com.kakao.actionbase.pipeline.runner.EmbeddedRunner
import com.kakao.actionbase.pipeline.workflow.WorkflowLoader
import org.junit.jupiter.api.{BeforeAll, Test}

object SparkPiJobTest {
  @BeforeAll
  def setupSpark(): Unit = {
    System.setProperty("spark.master", "local[2]")
    System.setProperty("spark.driver.bindAddress", "127.0.0.1")
    System.setProperty("spark.ui.enabled", "false")
  }
}

class SparkPiJobTest {

  @Test
  def runsJobFormEndToEnd(): Unit = {
    val wf = WorkflowLoader.load(WorkflowFixtures.path("spark-pi.yaml"))
    EmbeddedRunner.run(wf, Map("SAMPLES" -> "100000"))
  }

  @Test
  def runsStepsFormEndToEnd(): Unit = {
    val wf = WorkflowLoader.load(WorkflowFixtures.path("spark-pi-steps.yaml"))
    EmbeddedRunner.run(wf, Map("SAMPLES" -> "100000"))
  }
}
