package com.kakao.actionbase.pipeline.workflow

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import com.kakao.actionbase.pipeline.runner.{Expression, ExtendsResolver}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._

/** Loads a workflow YAML into a `Workflow` case class.
  *
  * Pipeline:
  *   1. Parse YAML to a raw `Map[String, Any]`. 2. Resolve `presets:` first (so its `$extends`/expressions resolve
  *      before downstream uses). 3. Build an `Expression.Context` with the resolved presets and any `env:` block. 4.
  *      Resolve `$extends` everywhere else in the tree. 5. Resolve remaining `${{ ... }}` expressions in string leaves
  *      (env / presets / load — `needs.*` is deferred to runtime since job results aren't yet known at load time). 6.
  *      Bind the resulting tree onto `Workflow` via Jackson.
  */
object WorkflowLoader {

  private[pipeline] lazy val mapper: ObjectMapper with ClassTagExtensions = {
    val m = new ObjectMapper(new YAMLFactory()) with ClassTagExtensions
    m.registerModule(DefaultScalaModule)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    m
  }

  def load(path: Path): Workflow = {
    val raw     = parseRaw(readUtf8(path))
    val baseDir = path.toAbsolutePath.getParent
    bind(raw, baseDir)
  }

  private def readUtf8(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  /** Used by tests / programmatic callers when YAML is already a string. `baseDir` is needed so `load(...)` expressions
    * can resolve relative paths.
    */
  def loadString(yaml: String, baseDir: Path): Workflow = bind(parseRaw(yaml), baseDir)

  private def parseRaw(yaml: String): Map[String, Any] = {
    val node = mapper.readValue(yaml, classOf[java.util.Map[String, Any]])
    if (node == null) Map.empty else node.asScala.toMap
  }

  private def bind(raw: Map[String, Any], baseDir: Path): Workflow = {
    // 1. env (raw, no expression evaluation — env values are static strings)
    val env: Map[String, String] = raw.get("env") match {
      case Some(m: java.util.Map[_, _]) =>
        m.asInstanceOf[java.util.Map[String, Any]].asScala.iterator.map { case (k, v) => k -> stringify(v) }.toMap
      case Some(m: scala.collection.Map[_, _]) =>
        m.asInstanceOf[scala.collection.Map[String, Any]].iterator.map { case (k, v) => k -> stringify(v) }.toMap
      case _ => Map.empty
    }

    val loader: String => Any = pathStr => {
      val p        = Paths.get(pathStr)
      val resolved = if (p.isAbsolute) p else baseDir.resolve(p).normalize()
      parseRaw(readUtf8(resolved))
    }

    // 2. resolve presets first with a context that has only env + load (presets refers to itself recursively
    //    only via `$extends: ${{ presets.X }}`, which would loop — current model: presets resolve in declaration order)
    val rawPresets = raw.get("presets") match {
      case Some(m: java.util.Map[_, _])        => m.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      case Some(m: scala.collection.Map[_, _]) => m.asInstanceOf[scala.collection.Map[String, Any]].toMap
      case _                                   => Map.empty
    }
    val presetCtx = Expression.Context(env = env, loadYaml = loader)
    val resolvedPresetsRaw = ExtendsResolver.resolve(rawPresets.asJava, presetCtx) match {
      case jm: java.util.Map[_, _] => jm.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      case _                       => Map.empty[String, Any]
    }
    val resolvedPresets: Map[String, Any] = Expression.resolveDeep(resolvedPresetsRaw.asJava, presetCtx) match {
      case jm: java.util.Map[_, _] => jm.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      case _                       => Map.empty
    }

    // 3. full context for the rest of the tree
    val fullCtx = Expression.Context(env = env, presets = resolvedPresets, loadYaml = loader)

    // 4. resolve $extends everywhere in the rest of the tree
    val withoutPresets  = raw - "presets"
    val extendsResolved = ExtendsResolver.resolve(withoutPresets.asJava, fullCtx)

    // 5. resolve remaining expressions in string leaves; needs.* and `when:` operator forms are deferred to runtime
    val resolved = Expression.resolveDeep(extendsResolved, fullCtx, lenient = true) match {
      case jm: java.util.Map[_, _] => jm.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      case other                   => sys.error(s"workflow root must be a map, got: $other")
    }

    // 6. attach the resolved presets back so the resulting Workflow case class carries them
    val withPresets = resolved + ("presets" -> resolvedPresets)

    mapper.convertValue(withPresets.asJava, classOf[Workflow])
  }

  private def stringify(value: Any): String = value match {
    case null      => ""
    case s: String => s
    case other     => other.toString
  }
}
