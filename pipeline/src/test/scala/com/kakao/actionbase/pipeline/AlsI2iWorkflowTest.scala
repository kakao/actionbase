package com.kakao.actionbase.pipeline

import com.kakao.actionbase.pipeline.runner.StepsBuilder
import com.kakao.actionbase.pipeline.workflow.StepSpec
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/**
  * End-to-end exercise of the Step DSL on the ALS i2i experiment: line-delimited edge JSON → SQL prep → ALS →
  * Top-K cosine similarity → Parquet sink. Doubles as a documentation example: the steps below mirror the YAML in
  * `pipeline/conf/als-i2i-experiment.yaml`.
  */
class AlsI2iWorkflowTest extends SparkTest {

  @Test
  def runsFullWorkflowOnSyntheticEdges(): Unit = {
    // Two clear interaction clusters so ALS recovers structure deterministically: users {0,1} love items {100,101},
    // users {2,3} love items {200,201}. Top-1 i2i neighbor of 100 should be 101, of 200 should be 201.
    val edgesPath = Files.createTempDirectory("als-edges-").resolve("edges.json").toString
    val payload =
      """{"source":0,"target":100,"version":1}
        |{"source":0,"target":101,"version":2}
        |{"source":1,"target":100,"version":3}
        |{"source":1,"target":101,"version":4}
        |{"source":2,"target":200,"version":5}
        |{"source":2,"target":201,"version":6}
        |{"source":3,"target":200,"version":7}
        |{"source":3,"target":201,"version":8}
        |""".stripMargin
    Files.write(Paths.get(edgesPath), payload.getBytes(StandardCharsets.UTF_8))

    val outPath = Files.createTempDirectory("als-i2i-out-").resolve("topk").toString

    val steps = Seq(
      StepSpec(
        "FileSource",
        Map("path" -> edgesPath, "format" -> "json")
      ),
      StepSpec(
        "SqlTransform",
        Map("query" -> "SELECT CAST(source AS INT) AS user, CAST(target AS INT) AS item, 1.0 AS rating FROM _0")
      ),
      StepSpec(
        "AlsTransform",
        Map("rank" -> 4, "maxIter" -> 10, "regParam" -> 0.01, "seed" -> 42L)
      ),
      StepSpec(
        "TopKSimilarityTransform",
        Map("k" -> 2)
      ),
      StepSpec(
        "FileSink",
        Map("path" -> outPath, "format" -> "parquet", "mode" -> "overwrite")
      )
    )

    StepsBuilder.build(steps).run()

    val result = spark.read.parquet(outPath)
    assertEquals(
      Set("item_id", "similar_item_id", "score", "rank"),
      result.columns.toSet
    )

    // Each item's top-1 neighbor should be its cluster sibling.
    val top1 = result
      .where("rank = 1")
      .collect()
      .map(r => r.getLong(0) -> r.getLong(1))
      .toMap

    assertEquals(101L, top1(100L), "100's top neighbor must be 101 (same cluster)")
    assertEquals(100L, top1(101L), "101's top neighbor must be 100 (same cluster)")
    assertEquals(201L, top1(200L), "200's top neighbor must be 201 (same cluster)")
    assertEquals(200L, top1(201L), "201's top neighbor must be 200 (same cluster)")
  }
}
