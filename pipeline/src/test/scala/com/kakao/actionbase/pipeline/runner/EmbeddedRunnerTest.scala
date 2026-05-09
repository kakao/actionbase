package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.dsl._
import com.kakao.actionbase.pipeline.steps.sink.FileSink
import com.kakao.actionbase.pipeline.steps.source.FileSource
import com.kakao.actionbase.pipeline.runtime.JobOutputs
import com.kakao.actionbase.pipeline.workflow.WorkflowLoader
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.{BeforeAll, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

// Minimal Jobs co-located with the test so the runner has something concrete
// to load via reflection without pulling domain example code into `main`.

case class StageCfg(in: String, out: String)
object StageJob extends Job[StageCfg] {
  override def plan(cfg: StageCfg): Plan.Closed = {
    emit("out", cfg.out)
    FileSource(cfg.in, "json") ~> FileSink(cfg.out, "parquet")
  }
}

case class CountCfg(in: String)
object CountJob extends Job[CountCfg] {
  override def plan(cfg: CountCfg): Plan.Closed =
    FileSource(cfg.in, "parquet") ~> RowCountSink("rows")
}

private case class RowCountSink(name: String) extends Sink {
  override def write(in: DataFrame)(implicit spark: SparkSession): Unit =
    JobOutputs.emit(name, in.count().toString)
}

object EmbeddedRunnerTest {
  @BeforeAll
  def setupSpark(): Unit = {
    System.setProperty("spark.master", "local[2]")
    System.setProperty("spark.driver.bindAddress", "127.0.0.1")
    System.setProperty("spark.ui.enabled", "false")
  }
}

class EmbeddedRunnerTest {

  private def workflowResource(name: String): String =
    Paths.get(getClass.getResource(s"/workflows/$name").toURI).toString

  private def writeInputJson(dir: Path): String = {
    val p = dir.resolve("in.json")
    Files.write(p, "{\"a\":1}\n{\"a\":2}\n{\"a\":3}\n".getBytes(StandardCharsets.UTF_8))
    p.toString
  }

  @Test
  def runsTwoJobsWithDynamicOutputs(): Unit = {
    val tmp = Files.createTempDirectory("pipeline-")
    val in  = writeInputJson(tmp)
    val out = tmp.resolve("staged").toString

    val wf = WorkflowLoader.load(workflowResource("minimal.yaml"))
    EmbeddedRunner.run(wf, Map("INPUT" -> in, "OUT" -> out))

    assertTrue(Files.exists(Paths.get(out)), s"$out should exist")
  }

  @Test
  def cascadeSkipsWhenIfFalse(): Unit = {
    val tmp = Files.createTempDirectory("pipeline-skip-")
    val in  = writeInputJson(tmp)
    val out = tmp.resolve("staged").toString

    val wf = WorkflowLoader.load(workflowResource("minimal-skip.yaml"))
    EmbeddedRunner.run(wf, Map("INPUT" -> in, "OUT" -> out))

    assertFalse(
      Files.exists(Paths.get(out)),
      s"$out must not exist when stage is skipped"
    )
  }
}
