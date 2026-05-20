package com.kakao.actionbase.pipeline.jobs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.file.{Files, Paths}

/**
  * End-to-end YAML round-trip for `StepsRunnerJob`: parses a workflow-shaped YAML `args:` block into the loose
  * `Map[String, Any]` shape that real runners hand to `Job.planFromMap`, then exercises Plan construction and
  * execution. Catches `StepSpec` / Cfg deserialize regressions that `StepsBuilderTest` (which starts from already-
  * constructed `StepSpec` values) cannot see — in particular, the polymorphic `as` field that resolves to either a
  * String or a Map depending on the step kind.
  */
class StepsRunnerJobTest extends SparkTest {

  private val yaml: ObjectMapper with ClassTagExtensions = {
    val m = new ObjectMapper(new YAMLFactory()) with ClassTagExtensions
    m.registerModule(DefaultScalaModule)
    m
  }

  private def yamlAsMap(text: String): Map[String, Any] = yaml.readValue[Map[String, Any]](text)

  @Test
  def runsLinearChainFromYaml(): Unit = {
    val text =
      """steps:
        |  - step: SampleSource
        |    args: { n: 10, columns: [x] }
        |  - step: SqlTransform
        |    args: { query: "SELECT x * 2 AS y FROM _0" }
        |  - step: ShowSink
        |""".stripMargin

    StepsRunnerJob.planFromMap(yamlAsMap(text)).run()
  }

  @Test
  def bindsStringAsAndInputsForJoin(): Unit = {
    val text =
      """steps:
        |  - step: SampleSource
        |    args: { n: 4, columns: [u] }
        |    as: users
        |  - step: SampleSource
        |    args: { n: 4, columns: [e] }
        |    as: events
        |  - step: SqlTransform
        |    args: { query: "SELECT u.u, e.e FROM users u CROSS JOIN events e" }
        |    inputs: [users, events]
        |  - step: ShowSink
        |""".stripMargin

    StepsRunnerJob.planFromMap(yamlAsMap(text)).run()
  }

  @Test
  def bindsMapAsForSplitPorts(): Unit = {
    val tmpEven = Files.createTempDirectory("yaml-split-even-").resolve("out").toString
    val tmpOdd  = Files.createTempDirectory("yaml-split-odd-").resolve("out").toString

    val text =
      s"""steps:
         |  - step: SampleSource
         |    args: { n: 6, columns: [v] }
         |  - step: SqlTransform
         |    args: { query: "SELECT CAST(monotonically_increasing_id() AS INT) AS k, v FROM _0" }
         |  - step: com.kakao.actionbase.pipeline.steps.split.ParitySplit
         |    as: { even: evenRows, odd: oddRows }
         |  - step: FileSink
         |    args: { path: "$tmpEven", format: parquet, mode: overwrite }
         |    inputs: [evenRows]
         |  - step: FileSink
         |    args: { path: "$tmpOdd", format: parquet, mode: overwrite }
         |    inputs: [oddRows]
         |""".stripMargin

    StepsRunnerJob.planFromMap(yamlAsMap(text)).run()

    assertTrue(Files.exists(Paths.get(tmpEven)))
    assertTrue(Files.exists(Paths.get(tmpOdd)))
    val even = spark.read.parquet(tmpEven).collect().map(_.getInt(0)).toSet
    val odd  = spark.read.parquet(tmpOdd).collect().map(_.getInt(0)).toSet
    assertTrue(even.forall(_ % 2 == 0), s"even partition leaked odd rows: $even")
    assertTrue(odd.forall(_ % 2 == 1), s"odd partition leaked even rows: $odd")
  }
}
