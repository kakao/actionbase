package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class AlsTransformTest extends SparkTest {

  /**
    * Builds a small, learnable interaction table: 4 users × 4 items where users {0,1} prefer items {0,1} and users
    * {2,3} prefer items {2,3}. ALS should recover two distinct factor regions.
    */
  private def interactions() = {
    import spark.implicits._
    Seq(
      (0, 0, 1.0f),
      (0, 1, 1.0f),
      (1, 0, 1.0f),
      (1, 1, 1.0f),
      (2, 2, 1.0f),
      (2, 3, 1.0f),
      (3, 2, 1.0f),
      (3, 3, 1.0f)
    ).toDF("user", "item", "rating")
  }

  @Test
  def fitsAndEmitsItemFactors(): Unit = {
    val out = AlsTransform(rank = 4, maxIter = 5).apply(interactions())

    assertEquals(Set("id", "features"), out.columns.toSet)
    val rows = out.collect()
    assertEquals(4, rows.length, "one factor row per item")
    rows.foreach { r =>
      val f = r.getAs[scala.collection.Seq[Float]]("features")
      assertEquals(4, f.size, "feature dim must equal rank")
    }
  }

  @Test
  def deterministicGivenSeed(): Unit = {
    // Same seed + same data ⇒ same factors. Guards against accidental nondeterminism in step construction.
    val a = AlsTransform(rank = 3, maxIter = 5, seed = 7L).apply(interactions()).collect()
    val b = AlsTransform(rank = 3, maxIter = 5, seed = 7L).apply(interactions()).collect()
    assertEquals(a.length, b.length)
    a.zip(b).foreach { case (ra, rb) =>
      assertEquals(ra.getInt(0), rb.getInt(0))
      val fa = ra.getAs[scala.collection.Seq[Float]](1)
      val fb = rb.getAs[scala.collection.Seq[Float]](1)
      fa.zip(fb).foreach { case (x, y) => assertEquals(x, y, 1e-6f) }
    }
  }
}
