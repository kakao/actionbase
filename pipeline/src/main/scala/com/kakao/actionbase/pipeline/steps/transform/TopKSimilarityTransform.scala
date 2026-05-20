package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.dsl.Flow
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
  * Given a `(idCol, featuresCol)` table of factor vectors, emits the top-`k` most similar items per item by cosine
  * similarity. Output schema is `(item_id long, similar_item_id long, score double, rank int)`.
  *
  * Computed via a cross self-join + Window `row_number` — O(N²) item-pairs, fine for experiment-scale catalogues but
  * not for production scale. For larger N, swap for an ANN index (e.g., `BucketedRandomProjectionLSH`) — but that
  * changes the contract (approximate) and belongs in a separate Step.
  */
case class TopKSimilarityTransform(
    k: Int = 20,
    idCol: String = "id",
    featuresCol: String = "features"
) extends Flow {

  override def apply(in: DataFrame)(implicit spark: SparkSession): DataFrame = {
    require(k > 0, s"k must be positive, got $k")
    val factors = in.select(col(idCol).as("id"), col(featuresCol).as("features"))

    // UDF: cosine similarity for two float feature arrays. Returns 0.0 when either vector is the zero vector to avoid
    // division-by-zero polluting the output with NaN.
    val cosine = udf { (a: Seq[Float], b: Seq[Float]) =>
      val n           = math.min(a.size, b.size)
      var dot, na, nb = 0.0
      var i           = 0
      while (i < n) {
        val ai = a(i).toDouble
        val bi = b(i).toDouble
        dot += ai * bi
        na += ai * ai
        nb += bi * bi
        i += 1
      }
      val denom = math.sqrt(na) * math.sqrt(nb)
      if (denom == 0.0) 0.0 else dot / denom
    }

    val pairs = factors
      .alias("a")
      .crossJoin(factors.alias("b"))
      .where(col("a.id") =!= col("b.id"))
      .select(
        col("a.id").cast(LongType).as("item_id"),
        col("b.id").cast(LongType).as("similar_item_id"),
        cosine(col("a.features"), col("b.features")).as("score")
      )

    val topK = Window.partitionBy("item_id").orderBy(col("score").desc, col("similar_item_id").asc)

    pairs
      .withColumn("rank", row_number().over(topK))
      .filter(col("rank") <= k)
  }
}
