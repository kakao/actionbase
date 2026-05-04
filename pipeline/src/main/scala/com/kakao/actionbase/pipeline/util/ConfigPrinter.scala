package com.kakao.actionbase.pipeline.util

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, SerializationFeature}
import com.fasterxml.jackson.dataformat.yaml.{YAMLFactory, YAMLGenerator, YAMLMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import java.util.regex.Pattern

import scala.collection.JavaConverters._

object ConfigPrinter {

  // Field-name patterns that look like credentials. Values for matching fields are masked
  // in both the per-field report and the YAML dump to avoid leaking secrets into driver logs.
  // Conservative on purpose — generic terms like "key" or "auth" are intentionally excluded
  // to avoid masking innocuous fields. Extend this pattern as new sensitive field names appear.
  private val SensitivePattern = Pattern.compile("(?i).*(password|passwd|secret|token|credential).*")
  private val Mask             = "***"

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
  //
  // Sensitive fields (see SensitivePattern) are masked everywhere they appear.
  // Note: the per-field section iterates only the top-level declared fields of T;
  // the trailing YAML dump renders the full tree (including nested objects), and
  // masking is applied recursively there.
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
        val name      = field.getName
        val sensitive = isSensitive(name)
        val value     = if (sensitive) Mask else display(field.get(parsed))

        val origin =
          if (argsMap.contains(name))       "args"
          else if (propsMap.contains(name)) "props"
          else if (envMap.contains(name))   "env"
          else                              "default"

        val trace = Seq(
          envMap.get(name).map(v => s"env=${maskValue(sensitive, v)}"),
          propsMap.get(name).map(v => s"props=${maskValue(sensitive, v)}"),
          argsMap.get(name).map(v => s"args=${maskValue(sensitive, v)}")
        ).flatten

        val tracePart = if (trace.isEmpty) "" else s"  (${trace.mkString(", ")})"
        println(s"  $name = $value  [$origin]$tracePart")
      }
    println("--- final ---")
    println(yaml.writeValueAsString(maskedTree(parsed)))
  }

  private[util] def isSensitive(name: String): Boolean = SensitivePattern.matcher(name).matches()

  private def maskValue(sensitive: Boolean, v: String): String = if (sensitive) Mask else v

  // Walks the serialized tree and replaces sensitive fields with the mask string.
  // Visible to tests so masking can be verified without capturing stdout.
  private[util] def maskedTree(parsed: Product): JsonNode = {
    val tree = yaml.valueToTree[JsonNode](parsed)
    maskInPlace(tree)
    tree
  }

  private def maskInPlace(node: JsonNode): Unit = node match {
    case obj: ObjectNode =>
      obj.fields().asScala.toSeq.foreach { entry =>
        if (isSensitive(entry.getKey)) obj.put(entry.getKey, Mask)
        else maskInPlace(entry.getValue)
      }
    case arr if arr.isArray => arr.elements().asScala.foreach(maskInPlace)
    case _                  => ()
  }

  private def display(value: Any): String = value match {
    case null          => "null"
    case arr: Array[_] => arr.mkString("[", ", ", "]")
    case other         => other.toString
  }
}
