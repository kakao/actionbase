package com.kakao.actionbase.pipeline.steps.source

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class FileSourceTest extends SparkTest {

  @Test
  def readsJson(): Unit = {
    val tmp  = Files.createTempDirectory("file-source-json-")
    val path = tmp.resolve("data.json").toString
    Files.write(
      Paths.get(path),
      "{\"a\":1}\n{\"a\":2}\n".getBytes(StandardCharsets.UTF_8)
    )

    val df = FileSource(path, "json").read()

    assertEquals(2L, df.count())
    assertTrue(df.columns.contains("a"))
  }

  @Test
  def readsParquet(): Unit = {
    val tmp = Files.createTempDirectory("file-source-parquet-")
    val src = tmp.resolve("src.json").toString
    Files.write(
      Paths.get(src),
      "{\"a\":1}\n{\"a\":2}\n{\"a\":3}\n".getBytes(StandardCharsets.UTF_8)
    )
    val parquet = tmp.resolve("out.parquet").toString
    spark.read.json(src).write.parquet(parquet)

    val df = FileSource(parquet, "parquet").read()

    assertEquals(3L, df.count())
  }

  @Test
  def passesOptionsToReader(): Unit = {
    val tmp  = Files.createTempDirectory("file-source-csv-")
    val path = tmp.resolve("data.csv").toString
    Files.write(
      Paths.get(path),
      "name,age\nalice,30\nbob,25\n".getBytes(StandardCharsets.UTF_8)
    )

    val df = FileSource(path, "csv", Map("header" -> "true")).read()

    assertEquals(2L, df.count())
    assertTrue(df.columns.toSet.contains("name"))
    assertTrue(df.columns.toSet.contains("age"))
  }
}
