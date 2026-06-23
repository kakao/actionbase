package com.kakao.actionbase.pipeline.util

import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import scala.reflect.ClassTag

object ConfigLoader {
  // Strict JSON mapper for config deserialization: missing required primitives must throw.
  @transient private lazy val json: ObjectMapper with ClassTagExtensions = {
    val mapper = new ObjectMapper() with ClassTagExtensions
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    mapper
  }

  // Cascading config: sources are merged left-to-right, so later sources win.
  // Default order is env < props < args.
  // Prints a per-field report by default; pass printReport = false to silence
  // (e.g. unit tests, batch tools) without changing other behavior.
  def load[T <: Product: ClassTag](
      args: Array[String] = Array.empty,
      printReport: Boolean = true
  ): T = {
    val sources   = Seq(EnvSource, PropsSource, ArgsSource(args)) // low → high
    val perSource = sources.map(s => s.name -> s.load())
    val merged    = perSource.map(_._2).reduce(_ ++ _)
    val parsed    = parse[T](merged)

    if (printReport) ConfigPrinter.printConfigReport(perSource, parsed)
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
}
