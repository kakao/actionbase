package com.kakao.actionbase.pipeline.steps.sink

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.file.{Files, Paths}

class FileSinkTest extends SparkTest {

  @Test
  def writesParquet(): Unit = {
    import spark.implicits._
    val df   = Seq(("a", 1), ("b", 2)).toDF("k", "v")
    val tmp  = Files.createTempDirectory("file-sink-parquet-")
    val path = tmp.resolve("out").toString

    FileSink(path, "parquet").write(df)

    assertTrue(Files.exists(Paths.get(path)))
    assertEquals(2L, spark.read.parquet(path).count())
  }

  @Test
  def writesJson(): Unit = {
    import spark.implicits._
    val df   = Seq(1, 2, 3).toDF("v")
    val tmp  = Files.createTempDirectory("file-sink-json-")
    val path = tmp.resolve("out").toString

    FileSink(path, "json").write(df)

    assertEquals(3L, spark.read.json(path).count())
  }

  @Test
  def overwriteReplacesExisting(): Unit = {
    import spark.implicits._
    val df1  = Seq(1, 2).toDF("v")
    val df2  = Seq(10, 20, 30).toDF("v")
    val tmp  = Files.createTempDirectory("file-sink-mode-")
    val path = tmp.resolve("out").toString

    FileSink(path, "parquet", mode = "overwrite").write(df1)
    FileSink(path, "parquet", mode = "overwrite").write(df2)

    assertEquals(3L, spark.read.parquet(path).count())
  }

  @Test
  def defaultModeFailsOnExisting(): Unit = {
    // Default `mode = "errorifexists"` matches Spark's own default — a second write to the same path must throw rather
    // than silently destroy the previous run's output.
    import spark.implicits._
    val df   = Seq(1).toDF("v")
    val tmp  = Files.createTempDirectory("file-sink-default-")
    val path = tmp.resolve("out").toString

    FileSink(path, "parquet").write(df)
    assertThrows(classOf[Throwable], () => FileSink(path, "parquet").write(df))
  }
}
