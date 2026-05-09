package com.kakao.actionbase.pipeline.steps.transform

import com.kakao.actionbase.pipeline.SparkTest
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

class SqlTransformTest extends SparkTest {

  @Test
  def projectsAndComputesColumns(): Unit = {
    import spark.implicits._
    val in = Seq((1, 2), (3, 4)).toDF("a", "b")

    val out = SqlTransform("SELECT a, b, a + b AS sum FROM _0").apply(in)

    assertEquals(Set("a", "b", "sum"), out.columns.toSet)
    val sums = out.collect().map(_.getAs[Int]("sum")).toSet
    assertEquals(Set(3, 7), sums)
  }

  @Test
  def usesProvidedLabelAsViewName(): Unit = {
    import spark.implicits._
    val in = Seq(1, 2, 3).toDF("n")

    val out = SqlTransform("SELECT SUM(n) AS total FROM nums").apply(Seq("nums" -> in))

    assertEquals(6L, out.first().getLong(0))
  }

  @Test
  def filtersRowsViaWhere(): Unit = {
    import spark.implicits._
    val in = Seq(1, 2, 3, 4).toDF("n")

    val out = SqlTransform("SELECT n FROM _0 WHERE n > 2").apply(in)

    assertEquals(Set(3, 4), out.collect().map(_.getInt(0)).toSet)
  }

  @Test
  def joinsTwoLabeledInputs(): Unit = {
    import spark.implicits._
    val users  = Seq((1, "alice"), (2, "bob")).toDF("id", "name")
    val events = Seq((1, "login"), (2, "click"), (2, "logout")).toDF("user_id", "kind")

    val out = SqlTransform(
      "SELECT u.name, e.kind FROM users u JOIN events e ON u.id = e.user_id"
    ).apply(Seq("users" -> users, "events" -> events))

    val rows = out.collect().map(r => r.getString(0) -> r.getString(1)).toSet
    assertEquals(Set("alice" -> "login", "bob" -> "click", "bob" -> "logout"), rows)
  }
}
