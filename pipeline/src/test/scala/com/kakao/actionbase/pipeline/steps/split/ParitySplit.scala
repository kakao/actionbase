package com.kakao.actionbase.pipeline.steps.split

import com.kakao.actionbase.pipeline.dsl.Split
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
  * Test-only Split that routes input rows by even/odd value of column `k`. Used by `StepsBuilderTest` to exercise the
  * Split DSL/YAML wiring without depending on a heavier production Split class. Lives under `steps.split` so
  * `ClassResolver` resolves the short name during tests; the package itself is also the future home of production Split
  * built-ins.
  */
case class ParitySplit() extends Split {
  override def ports: Seq[String] = Seq("even", "odd")
  override def split(in: DataFrame)(implicit spark: SparkSession): Map[String, DataFrame] =
    Map(
      "even" -> in.where("k % 2 = 0"),
      "odd"  -> in.where("k % 2 = 1")
    )
}
