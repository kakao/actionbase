package com.kakao.actionbase.pipeline.dsl

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import org.apache.spark.sql.SparkSession

import scala.reflect.ClassTag

// spark-submit entry point. Subclass and implement `plan(cfg)`. `main` binds `--key=value` argv into Cfg;
// `planFromMap` binds a YAML-shaped Map (for in-process runners that need nested fields argv can't express).
abstract class Job[C <: Product: ClassTag] {

  def plan(cfg: C): Plan.Closed

  protected def configure(builder: SparkSession.Builder): SparkSession.Builder = builder

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

  // Lax — Spark adds its own `--spark.*` flags we don't model.
  private[pipeline] lazy val mapper: ObjectMapper with ClassTagExtensions =
    buildMapper(failOnUnknownProperties = false)

  // Strict — YAML-authored step args; typos must surface.
  private[pipeline] lazy val stepMapper: ObjectMapper with ClassTagExtensions =
    buildMapper(failOnUnknownProperties = true)

  private def buildMapper(failOnUnknownProperties: Boolean): ObjectMapper with ClassTagExtensions = {
    val m = new ObjectMapper() with ClassTagExtensions
    m.registerModule(DefaultScalaModule)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties)
    m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    m
  }

  private[pipeline] def parseArgv(argv: Array[String]): Map[String, Any] =
    argv.iterator.flatMap { s =>
      if (!s.startsWith("--") || !s.contains('=')) {
        System.err.println(s"[Job] ignoring unrecognized arg: '$s' (expected --key=value)")
        None
      } else {
        s.drop(2).split("=", 2) match {
          case Array(k, v) => Some(k -> v)
          case _ =>
            System.err.println(s"[Job] ignoring malformed arg: '$s' (expected --key=value)")
            None
        }
      }
    }.toMap
}
