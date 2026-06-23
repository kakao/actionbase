package com.kakao.actionbase.pipeline.steps.sink

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Test

class ShowSinkTest extends SparkTest {

  @Test
  def writesDataframeWithoutError(): Unit = {
    import spark.implicits._
    val in = Seq((1, "a"), (2, "b")).toDF("n", "name")

    ShowSink().write(in)
    ShowSink(numRows = 1, truncate = false).write(in)
  }
}
