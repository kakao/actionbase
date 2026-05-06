package com.kakao.actionbase.pipeline.util

import scala.collection.JavaConverters._

trait ConfigSource {
  def name: String
  def load(): Map[String, String]
}

object ConfigSource {
  // foo_bar -> fooBar (sep='_'); foo.bar -> fooBar (sep='.')
  private[util] def camelize(s: String, sep: Char): String = {
    val parts = s.split(sep).map(_.toLowerCase)
    parts.head + parts.tail.map(_.capitalize).mkString
  }
}

// SPARK_AB_FOO_BAR -> fooBar
object EnvSource extends ConfigSource {
  private val Prefix = "SPARK_AB_"
  val name           = "env"

  def load(): Map[String, String] =
    System.getenv().asScala.toMap.collect {
      case (k, v) if k.startsWith(Prefix) => ConfigSource.camelize(k.stripPrefix(Prefix), '_') -> v
    }
}

// spark.ab.foo.bar -> fooBar
object PropsSource extends ConfigSource {
  private val Prefix = "spark.ab."
  val name           = "props"

  def load(): Map[String, String] =
    System.getProperties.asScala.toMap.collect {
      case (k, v) if k.startsWith(Prefix) => ConfigSource.camelize(k.stripPrefix(Prefix), '.') -> v
    }
}

// Only --key=value form is supported. Splits on the first '='; later '=' chars stay in the value.
case class ArgsSource(args: Array[String]) extends ConfigSource {
  val name = "args"

  def load(): Map[String, String] =
    args.iterator.map { arg =>
      require(arg.startsWith("--"), s"Expected --key=value, got: $arg")
      val body = arg.drop(2)
      val eq   = body.indexOf('=')
      require(eq > 0, s"Expected --key=value, got: $arg")
      body.substring(0, eq) -> body.substring(eq + 1)
    }.toMap
}
