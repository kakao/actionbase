package com.kakao.actionbase.pipeline.dsl

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import org.apache.spark.sql.SparkSession

import scala.reflect.ClassTag

/** One spark-submit unit. Subclass and implement `plan(cfg)`.
  *
  * Two entry points share the same Cfg-binding mapper:
  *   - `main(argv)` — for spark-submit / CLI: parses `--key=value` argv into Cfg.
  *   - `planFromMap(args)` — for in-process runners: binds an arbitrary YAML-shaped Map onto Cfg, supporting nested
  *     fields like `Seq[StepSpec]` that argv cannot represent.
  *
  * {{{
  * case class MyConfig(in: String, out: String)
  *
  * object MyJob extends Job[MyConfig] {
  *   override def plan(cfg: MyConfig): Plan.Closed =
  *     FileSource(cfg.in, "parquet") ~> MyTransform() ~> FileSink(cfg.out, "parquet")
  * }
  * }}}
  */
abstract class Job[C <: Product: ClassTag] {

  def plan(cfg: C): Plan.Closed

  /** Hook for subclasses to tweak the SparkSession builder (master, configs). */
  protected def configure(builder: SparkSession.Builder): SparkSession.Builder = builder

  /** Bind `args` onto Cfg via Jackson and return the resulting Plan. Used by runners that already manage Spark. */
  def planFromMap(args: Map[String, Any]): Plan.Closed = {
    val cls = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]
    val cfg = Job.mapper.convertValue(args, cls)
    plan(cfg)
  }

  def main(argv: Array[String]): Unit = {
    val args = Job.parseArgv(argv)
    println(s"Running ${getClass.getSimpleName}: $args")

    val spark = configure(
      SparkSession.builder().appName(getClass.getCanonicalName.stripSuffix("$"))
    ).getOrCreate()

    try planFromMap(args).run()(spark)
    finally {
      println("Stopping Spark session...")
      spark.stop()
    }
  }
}

object Job {

  // Strict mapper: missing required primitives must throw rather than silently
  // become 0/false. Unknown keys are tolerated so `--extra=...` doesn't fail.
  @transient private[pipeline] lazy val mapper: ObjectMapper with ClassTagExtensions = {
    val m = new ObjectMapper() with ClassTagExtensions
    m.registerModule(DefaultScalaModule)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    m
  }

  private def parseArgv(argv: Array[String]): Map[String, Any] =
    argv.iterator.flatMap { s =>
      if (!s.startsWith("--")) None
      else
        s.drop(2).split("=", 2) match {
          case Array(k, v) => Some(k -> v)
          case _           => None
        }
    }.toMap
}
