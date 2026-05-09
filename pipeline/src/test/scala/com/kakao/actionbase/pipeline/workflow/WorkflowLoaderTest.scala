package com.kakao.actionbase.pipeline.workflow

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.file.Paths

class WorkflowLoaderTest {

  private def resourcePath(name: String): String =
    Paths.get(getClass.getResource(s"/workflows/$name").toURI).toString

  @Test
  def loadsPresetsFromAdjacentDirectory(): Unit = {
    val wf = WorkflowLoader.load(resourcePath("with-preset.yaml"))
    assertTrue(wf.presets.contains("test-small"), s"presets keys: ${wf.presets.keys}")

    val preset = wf.presets("test-small")
    assertEquals("1g", preset("executor-memory"))
    assertEquals("512m", preset("driver-memory"))
    val conf = preset("conf").asInstanceOf[Map[String, Any]]
    assertEquals(4, conf("spark.sql.shuffle.partitions"))
  }

  @Test
  def workflowWithoutPresetDirectoryStillLoads(): Unit = {
    val wf = WorkflowLoader.load(resourcePath("minimal.yaml"))
    assertEquals("minimal", wf.name)
    // The preset/ folder may exist (shared with other test fixtures); we just assert load succeeded.
  }
}
