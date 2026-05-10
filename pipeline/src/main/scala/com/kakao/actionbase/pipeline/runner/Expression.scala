package com.kakao.actionbase.pipeline.runner

import scala.collection.JavaConverters._
import scala.util.matching.Regex

/** Resolves `${{ ... }}` expressions in a parsed YAML tree against a context.
  *
  * Vocabulary:
  *   - `env.<key>` — workflow `env:` value (string)
  *   - `needs.<id>.result` — upstream job result: `success` / `failure` / `skipped` / `cancelled`
  *   - `needs.<id>.outputs.<key>` — value emitted by an upstream job
  *   - `presets.<name>` — entry from the workflow `presets:` section (any value, often a map)
  *   - `load('<path>')` — content of another YAML file at `<path>` relative to the workflow file's dir
  *
  * An expression that fills the entire string value (e.g. `"${{ presets.X }}"`) is replaced by the raw evaluated value
  * (which may be a map / list, not just a string). Otherwise expressions are stringified and substituted within the
  * surrounding text.
  */
object Expression {

  /** Inputs the evaluator needs. `needs` is empty at YAML-load time and populated at runtime. `loadYaml` is used by the
    * `load(...)` form; injected so test mocks don't have to touch the file system.
    */
  case class Context(
      env: Map[String, String] = Map.empty,
      presets: Map[String, Any] = Map.empty,
      needs: Map[String, NeedsView] = Map.empty,
      loadYaml: String => Any = _ => sys.error("load(...) not configured")
  )

  case class NeedsView(result: String, outputs: Map[String, String] = Map.empty)

  private val Token: Regex      = """\$\{\{\s*(.+?)\s*\}\}""".r
  private val WholeToken: Regex = """\A\s*\$\{\{\s*(.+?)\s*\}\}\s*\z""".r
  private val LoadCall: Regex   = """\Aload\(\s*'([^']*)'\s*\)\z""".r

  /** Resolve every string leaf in a YAML-shaped tree. Map / List structure preserved.
    *
    * `lenient = true` preserves the original `${{ ... }}` token when an expression cannot be evaluated against the
    * current context (typically: `needs.*` at load-time, or operator forms like `needs.X.result == 'success'` inside
    * `when:` that this evaluator does not parse). At runtime the same tree is walked again with full context to finish
    * those.
    */
  def resolveDeep(value: Any, ctx: Context, lenient: Boolean = false): Any = value match {
    case s: String =>
      s match {
        case WholeToken(expr) => evaluate(expr, ctx, lenient) // raw value, may be map / list
        case other =>
          Token.replaceAllIn(other, m => Regex.quoteReplacement(stringify(evaluate(m.group(1), ctx, lenient))))
      }
    case m: java.util.Map[_, _] =>
      m.asInstanceOf[java.util.Map[String, Any]]
        .asScala
        .map { case (k, v) => k -> resolveDeep(v, ctx, lenient) }
        .toMap
        .asJava
    case m: scala.collection.Map[_, _] =>
      m.asInstanceOf[scala.collection.Map[String, Any]].map { case (k, v) => k -> resolveDeep(v, ctx, lenient) }.toMap
    case l: java.util.List[_] =>
      l.asInstanceOf[java.util.List[Any]].asScala.map(resolveDeep(_, ctx, lenient)).asJava
    case l: scala.collection.Iterable[_] =>
      l.map(resolveDeep(_, ctx, lenient)).toSeq
    case other => other
  }

  /** Evaluate an expression body (without the outer `${{ }}`) and return the raw value.
    *
    * In lenient mode, an unresolvable expression (unknown key, unsupported syntax) returns the original `${{ ... }}`
    * string unchanged so it can be evaluated later by a fuller context.
    */
  def evaluate(expr: String, ctx: Context, lenient: Boolean = false): Any = {
    try evaluateImpl(expr, ctx)
    catch {
      case _: NoSuchElementException if lenient   => "${{ " + expr.trim + " }}"
      case _: IllegalArgumentException if lenient => "${{ " + expr.trim + " }}"
    }
  }

  private def evaluateImpl(expr: String, ctx: Context): Any = {
    val e = expr.trim
    if (e.startsWith("env.")) {
      val k = e.stripPrefix("env.")
      ctx.env.getOrElse(k, throw new NoSuchElementException(s"unknown env key: $k"))
    } else if (e.startsWith("presets.")) {
      val k = e.stripPrefix("presets.")
      ctx.presets.getOrElse(k, throw new NoSuchElementException(s"unknown preset: $k"))
    } else if (e.startsWith("needs.")) {
      evalNeeds(e.stripPrefix("needs."), ctx)
    } else if (e.startsWith("load(")) {
      e match {
        case LoadCall(path) => ctx.loadYaml(path)
        case _              => throw new IllegalArgumentException(s"malformed load(): $e — expected `load('path')`")
      }
    } else {
      throw new IllegalArgumentException(s"unknown expression: $e")
    }
  }

  private def evalNeeds(rest: String, ctx: Context): Any = {
    val parts = rest.split('.')
    if (parts.length < 2) throw new IllegalArgumentException(s"malformed needs.* expression: needs.$rest")
    val id   = parts(0)
    val view = ctx.needs.getOrElse(id, throw new NoSuchElementException(s"unknown needs id: $id"))
    parts(1) match {
      case "result" => view.result
      case "outputs" =>
        if (parts.length != 3)
          throw new IllegalArgumentException(s"needs.$id.outputs requires a key: needs.$id.outputs.<name>")
        view.outputs.getOrElse(
          parts(2),
          throw new NoSuchElementException(s"unknown output: needs.$id.outputs.${parts(2)}")
        )
      case other => throw new IllegalArgumentException(s"unknown needs field: needs.$id.$other")
    }
  }

  private def stringify(value: Any): String = value match {
    case null      => ""
    case s: String => s
    case other     => other.toString
  }
}
