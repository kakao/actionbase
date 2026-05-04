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

  // Only --key=value form is supported. Splits on the first '='; later '=' chars stay in the value.
  private def loadConfigFromArgs(args: Array[String]): Map[String, String] = {
    args.iterator.map { arg =>
      require(arg.startsWith("--"), s"Expected --key=value, got: $arg")
      val body = arg.drop(2)
      val eq   = body.indexOf('=')
      require(eq > 0, s"Expected --key=value, got: $arg")
      body.substring(0, eq) -> body.substring(eq + 1)
    }.toMap
  }

  // Array literal: [a, b, c]. Splits on top-level commas only — commas inside
  // double-quoted segments or escaped as \, are preserved. Empty body ([]) and
  // empty/whitespace-only tokens (trailing commas, [a,,b]) yield no element.
  private def toJsonNode(s: String): JsonNode = {
    val str = s.trim
    if (str.startsWith("[") && str.endsWith("]")) {
      val arrNode = json.createArrayNode()
      val inner   = str.substring(1, str.length - 1)
      splitTopLevelCommas(inner)
        .map(_.trim)
        .filter(_.nonEmpty)
        .foreach(elem => arrNode.add(toJsonNode(elem)))
      arrNode
    } else {
      json.getNodeFactory.textNode(s)
    }
  }

  // Top-level comma splitter. Within double quotes, commas are preserved.
  // A backslash escapes the very next character — so \, yields a literal comma
  // and \" yields a literal quote. Note: this is "literal next char" semantics,
  // i.e. \n is the letter n, not a newline; \\ is a single backslash.
  private def splitTopLevelCommas(s: String): Seq[String] = {
    val out     = scala.collection.mutable.ListBuffer[String]()
    val cur     = new StringBuilder
    var inQuote = false
    var i       = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '\\' && i + 1 < s.length) {
        cur.append(s.charAt(i + 1))
        i += 2
      } else if (c == '"') {
        inQuote = !inQuote
        cur.append(c)
        i += 1
      } else if (c == ',' && !inQuote) {
        out += cur.toString
        cur.clear()
        i += 1
      } else {
        cur.append(c)
        i += 1
      }
    }
    out += cur.toString
    out.toSeq
  }

  private def camelize(s: String, sep: Char): String = {
    val parts = s.split(sep).map(_.toLowerCase)
    parts.head + parts.tail.map(_.capitalize).mkString
  }
}
