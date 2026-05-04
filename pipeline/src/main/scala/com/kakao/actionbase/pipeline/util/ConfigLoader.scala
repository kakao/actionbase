package com.kakao.actionbase.pipeline.util

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object ConfigLoader {
  private val PropPrefix = "spark.ab."
  private val EnvPrefix  = "SPARK_AB_"

  // Strict JSON mapper for config deserialization: missing required primitives must throw.
  @transient private lazy val json: ObjectMapper with ClassTagExtensions = {
    val mapper = new ObjectMapper() with ClassTagExtensions
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    mapper
  }

  def load[T <: Product: ClassTag](): T = {
    load(Array.empty[String])
  }

  // Always prints a per-field report (env/props/args trace + final value).
  def load[T <: Product: ClassTag](args: Array[String]): T = {
    val envMap   = loadConfigFromEnvironment()
    val propsMap = loadConfigFromProperties()
    val argsMap  = loadConfigFromArgs(args)

    // preference order: args > properties > environment > defaults
    val configMap = envMap ++ propsMap ++ argsMap
    val parsed    = parse[T](configMap)

    ConfigPrinter.printConfigReport(envMap, propsMap, argsMap, parsed)
    parsed
  }

  private def parse[T <: Product: ClassTag](configMap: Map[String, String]): T = {
    val root = json.createObjectNode()
    configMap.foreach { case (k, v) =>
      root.set[JsonNode](k, toJsonNode(v))
    }
    val cls = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    json.convertValue(root, cls)
  }

  // SPARK_AB_FOO_BAR -> fooBar
  private def loadConfigFromEnvironment(): Map[String, String] = {
    System.getenv().asScala.toMap.collect {
      case (k, v) if k.startsWith(EnvPrefix) => camelize(k.stripPrefix(EnvPrefix), '_') -> v
    }
  }

  // spark.ab.foo.bar -> fooBar
  private def loadConfigFromProperties(): Map[String, String] = {
    System.getProperties.asScala.toMap.collect {
      case (k, v) if k.startsWith(PropPrefix) => camelize(k.stripPrefix(PropPrefix), '.') -> v
    }
  }

  private def loadConfigFromArgs(args: Array[String]): Map[String, String] = {
    args
      .sliding(2, 2)
      .collect {
        case Array(k, v) if k.startsWith("--") => k.drop(2) -> v
      }
      .toMap
  }

  private def toJsonNode(s: String): JsonNode = {
    val str = s.trim
    if (str.startsWith("[") && str.endsWith("]")) {
      val arrNode = json.createArrayNode()
      str.drop(1).dropRight(1).split(",").foreach { elem =>
        arrNode.add(toJsonNode(elem.trim))
      }
      arrNode
    } else {
      json.getNodeFactory.textNode(s)
    }
  }

  private def camelize(s: String, sep: Char): String = {
    val parts = s.split(sep).map(_.toLowerCase)
    parts.head + parts.tail.map(_.capitalize).mkString
  }
}
