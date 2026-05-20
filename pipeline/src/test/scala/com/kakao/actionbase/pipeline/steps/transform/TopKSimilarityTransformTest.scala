package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class TopKSimilarityTransformTest extends SparkTest {

  /**
    * Hand-crafted 4-item factor table where (0,1) are colinear and (2,3) are colinear, so the expected top-1 neighbor
    * pairs are 0↔1 and 2↔3. Lets us verify both ranking and cosine math without depending on ALS.
    */
  private def factors() = {
    import spark.implicits._
    Seq(
      (0, Seq(1.0f, 0.0f)),
      (1, Seq(2.0f, 0.0f)),
      (2, Seq(0.0f, 1.0f)),
      (3, Seq(0.0f, 3.0f))
    ).toDF("id", "features")
  }

  @Test
  def emitsSchemaContract(): Unit = {
    val out = TopKSimilarityTransform(k = 1).apply(factors())
    assertEquals(Seq("item_id", "similar_item_id", "score", "rank"), out.columns.toSeq)
    out.schema("item_id").dataType.toString.toLowerCase.contains("long")
    out.schema("similar_item_id").dataType.toString.toLowerCase.contains("long")
  }

  @Test
  def computesCorrectTopNeighbors(): Unit = {
    val rows  = TopKSimilarityTransform(k = 1).apply(factors()).collect()
    val pairs = rows.map(r => r.getLong(0) -> r.getLong(1)).toSet
    // Each item's top-1 partner: 0→1, 1→0, 2→3, 3→2. Colinear vectors have cosine=1.0 vs. orthogonal=0.0.
    assertEquals(Set(0L -> 1L, 1L -> 0L, 2L -> 3L, 3L -> 2L), pairs)
    rows.foreach { r => assertEquals(1.0, r.getDouble(2), 1e-6, s"score for ${r.getLong(0)}→${r.getLong(1)}") }
  }

  @Test
  def respectsKLimit(): Unit = {
    val out     = TopKSimilarityTransform(k = 2).apply(factors())
    val perItem = out.collect().groupBy(_.getLong(0)).map { case (id, rs) => id -> rs.length }
    perItem.values.foreach(n => assertTrue(n <= 2, s"per-item rows must not exceed k=2 (got $n)"))
  }

  @Test
  def rejectsNonPositiveK(): Unit = {
    assertThrows(
      classOf[IllegalArgumentException],
      () => TopKSimilarityTransform(k = 0).apply(factors())
    )
  }

  @Test
  def returnsZeroForZeroVector(): Unit = {
    // Zero-vector input is a legitimate edge case (e.g., cold-start factor before training): cosine is undefined and we
    // contract on returning 0.0 instead of NaN so downstream rank order stays well-defined.
    import spark.implicits._
    val df = Seq(
      (10, Seq(0.0f, 0.0f)),
      (11, Seq(1.0f, 1.0f))
    ).toDF("id", "features")
    val rows = TopKSimilarityTransform(k = 1).apply(df).collect()
    rows.foreach { r =>
      val s = r.getDouble(2)
      assertEquals(0.0, s, 1e-9, "zero-vector cosine must be 0.0, not NaN")
    }
  }
}
