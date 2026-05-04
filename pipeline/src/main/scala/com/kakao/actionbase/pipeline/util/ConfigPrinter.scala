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
  def printConfigReport[T <: Product](
      envMap: Map[String, String],
      propsMap: Map[String, String],
      argsMap: Map[String, String],
      parsed: T
  ): Unit = {
    println("=== Configuration ===")
    parsed.getClass.getDeclaredFields.toSeq
      .filterNot(_.getName.contains("$"))
      .foreach { field =>
        field.setAccessible(true)
        val name  = field.getName
        val value = display(field.get(parsed))

        val origin =
          if (argsMap.contains(name))       "args"
          else if (propsMap.contains(name)) "props"
          else if (envMap.contains(name))   "env"
          else                              "default"

        val trace = Seq(
          envMap.get(name).map(v => s"env=$v"),
          propsMap.get(name).map(v => s"props=$v"),
          argsMap.get(name).map(v => s"args=$v")
        ).flatten

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
