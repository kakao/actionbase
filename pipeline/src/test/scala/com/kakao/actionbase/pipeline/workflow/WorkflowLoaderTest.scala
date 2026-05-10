package com.kakao.actionbase.pipeline.workflow

import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.file.{Path, Paths}
import scala.collection.JavaConverters._

class WorkflowLoaderTest {

  private val ResourcesRoot: Path =
    Paths.get(getClass.getResource("/workflows").toURI)

  @Test
  def loadsBasicWorkflow(): Unit = {
    val wf = WorkflowLoader.load(ResourcesRoot.resolve("spark-pi.yaml"))

    assertEquals("spark-pi", wf.name)
    assertEquals("1000000", wf.env("samples"))
    assertEquals(2, wf.jobs.size)
    assertEquals("spark", wf.jobs("pi").kind)
    assertEquals(Some("SparkPiJob"), wf.jobs("pi").mainClass)
    assertEquals(Some("com.kakao.actionbase:pipeline:0.x"), wf.jobs("pi").artifact)
  }

  @Test
  def resolvesEnvExpressionInArgs(): Unit = {
    val wf = WorkflowLoader.load(ResourcesRoot.resolve("spark-pi.yaml"))
    val pi = wf.jobs("pi")
    assertEquals("1000000", pi.args("samples"))
  }

  @Test
  def resolvesPresetExtendsInSubmit(): Unit = {
    val wf     = WorkflowLoader.load(ResourcesRoot.resolve("spark-pi.yaml"))
    val submit = wf.jobs("pi").submit
    // From preset spark-small
    assertEquals("1g", submit("driver-memory"))
    assertEquals("2g", submit("executor-memory"))
    // From sibling override
    val conf = submit("conf").asInstanceOf[scala.collection.Map[String, Any]]
    assertEquals(8, conf("spark.sql.shuffle.partitions"))
  }

  @Test
  def resolvesLoadExtendsAgainstFile(): Unit = {
    val wf     = WorkflowLoader.load(ResourcesRoot.resolve("extends-via-load.yaml"))
    val submit = wf.jobs("pi").submit
    assertEquals("1g", submit("driver-memory"))
    assertEquals("2g", submit("executor-memory"))
    val conf = submit("conf").asInstanceOf[scala.collection.Map[String, Any]]
    assertEquals(8, conf("spark.sql.shuffle.partitions"))
  }

  @Test
  def carriesNeedsAndWhen(): Unit = {
    val wf     = WorkflowLoader.load(ResourcesRoot.resolve("spark-pi.yaml"))
    val report = wf.jobs("report")
    assertEquals(Seq("pi"), report.needs)
    assertTrue(report.`when`.isDefined)
    assertTrue(report.`when`.get.contains("needs.pi.result"))
  }

  @Test
  def deferredNeedsExpressionsArePreservedAtLoadTime(): Unit = {
    // `needs.*` is runtime-only — should remain unresolved in the loaded Workflow's `when` field.
    val wf  = WorkflowLoader.load(ResourcesRoot.resolve("spark-pi.yaml"))
    val raw = wf.jobs("report").`when`.get
    assertTrue(raw.contains("${{"), s"expected unresolved expression, got: $raw")
  }
}
