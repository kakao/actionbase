package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Flow
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
  * Fits Spark MLlib's ALS to a `(userCol, itemCol, ratingCol)` triple table and emits the trained model's `itemFactors`
  * — `(id: int, features: array<float>)` — for downstream i2i similarity.
  *
  * Defaults target implicit-feedback workloads (action presence ⇒ confidence 1.0): `implicitPrefs=true`,
  * `coldStartStrategy="drop"`. The two ID columns must already be `int` (or castable); upstream is responsible for
  * indexing arbitrary IDs (see the example workflow's `SqlTransform` prep step).
  *
  * Hyperparameters mirror `org.apache.spark.ml.recommendation.ALS`'s setters; defaults match Spark's defaults except
  * `implicitPrefs` (flipped to `true`) and `coldStartStrategy` (`"drop"` instead of `"nan"` so downstream Sinks don't
  * receive NaN rows from unseen IDs).
  *
  * Emits item factors only (single output). To persist both item and user factors, swap for a future `AlsFactorSplit`
  * (1→2 Split) once Split built-ins land.
  */
case class AlsTransform(
    userCol: String = "user",
    itemCol: String = "item",
    ratingCol: String = "rating",
    rank: Int = 10,
    maxIter: Int = 10,
    regParam: Double = 0.1,
    implicitPrefs: Boolean = true,
    alpha: Double = 1.0,
    coldStartStrategy: String = "drop",
    seed: Long = 42L
) extends Flow {

  override def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val als = new ALS()
      .setUserCol(userCol)
      .setItemCol(itemCol)
      .setRatingCol(ratingCol)
      .setRank(rank)
      .setMaxIter(maxIter)
      .setRegParam(regParam)
      .setImplicitPrefs(implicitPrefs)
      .setAlpha(alpha)
      .setColdStartStrategy(coldStartStrategy)
      .setSeed(seed)

    als.fit(in).itemFactors
  }
}
