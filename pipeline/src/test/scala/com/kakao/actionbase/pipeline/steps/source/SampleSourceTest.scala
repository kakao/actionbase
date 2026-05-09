package com.kakao.actionbase.pipeline.steps.source

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class SampleSourceTest extends SparkTest {

  @Test
  def producesRowsForRequestedColumns(): Unit = {
    val df = SampleSource(1000L, Seq("x", "y")).read()
    assertEquals(1000L, df.count())
    assertEquals(Set("x", "y"), df.columns.toSet)
  }

  @Test
  def supportsArbitraryColumnSet(): Unit = {
    val df = SampleSource(50L, Seq("a", "b", "c", "d")).read()
    assertEquals(50L, df.count())
    assertEquals(Set("a", "b", "c", "d"), df.columns.toSet)
  }

  @Test
  def valuesAreInUnitInterval(): Unit = {
    val df   = SampleSource(200L, Seq("x", "y")).read()
    val rows = df.collect()
    assertTrue(rows.forall { r =>
      val x = r.getAs[Double]("x")
      val y = r.getAs[Double]("y")
      x >= 0.0 && x < 1.0 && y >= 0.0 && y < 1.0
    })
  }

  @Test
  def rejectsEmptyColumnList(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => SampleSource(10L, Seq.empty))
  }
}
