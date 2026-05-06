package com.kakao.actionbase.pipeline.util

import com.fasterxml.jackson.databind.{DeserializationFeature, SerializationFeature}
import com.fasterxml.jackson.dataformat.yaml.{YAMLFactory, YAMLGenerator, YAMLMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

object ConfigPrinter {

  @transient private lazy val yaml: YAMLMapper with ClassTagExtensions = {
    val factory = new YAMLFactory()
    factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
    factory.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)

    val mapper = new YAMLMapper(factory) with ClassTagExtensions
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    mapper
  }

  // Per-field report: for each field of T, show the final value, the winning source,
  // and a trace of every source that contributed a value. Ends with the full YAML dump.
  // Sources arrive in low→high precedence order; the last contributor wins.
  def printConfigReport[T <: Product](
      sources: Seq[(String, Map[String, String])],
      parsed: T
  ): Unit = {
    println("=== Configuration ===")
    parsed.getClass.getDeclaredFields.toSeq
      .filterNot(_.getName.contains("$"))
      .foreach { field =>
        field.setAccessible(true)
        val name  = field.getName
        val value = display(field.get(parsed))

        val origin = sources.reverse
          .collectFirst { case (srcName, m) if m.contains(name) => srcName }
          .getOrElse("default")

        val trace = sources.flatMap { case (srcName, m) =>
          m.get(name).map(v => s"$srcName=$v")
        }

        val tracePart = if (trace.isEmpty) "" else s"  (${trace.mkString(", ")})"
        println(s"  $name = $value  [$origin]$tracePart")
      }
    println("--- final ---")
    println(yaml.writeValueAsString(parsed))
  }

  private def display(value: Any): String = value match {
    case null          => "null"
    case arr: Array[_] => arr.mkString("[", ", ", "]")
    case other         => other.toString
  }
}
