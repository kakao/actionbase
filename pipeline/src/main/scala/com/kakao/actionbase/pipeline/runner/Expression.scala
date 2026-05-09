package com.kakao.actionbase.pipeline.runner

import scala.collection.JavaConverters._
import scala.util.matching.Regex

/** Resolves `${{ ... }}` expressions in workflow YAML against a context tree. Supported paths:
  *   - `env.<name>` → Workflow `env:` block
  *   - `needs.<jobId>.outputs.<name>` → outputs emitted by an upstream job
  *
  * Resolution is one-pass. Nested expressions inside another expression's resolved value are not re-evaluated,
  * mirroring GitHub Actions semantics.
  *
  * `evaluateBoolean` handles `if:` conditions: truthy check by default, with optional `==` / `!=` string comparison.
  * Outer `${{ ... }}` braces are optional in `if:` since the value is always an expression.
  */
object Expression {

  private val pattern: Regex = """\$\{\{\s*([^}]+?)\s*\}\}""".r
  private val eqRe: Regex    = """\A(.+?)\s*==\s*(.+)\z""".r
  private val neqRe: Regex   = """\A(.+?)\s*!=\s*(.+)\z""".r

  def resolve(input: String, ctx: Context): String =
    pattern.replaceAllIn(input, m => Regex.quoteReplacement(lookup(m.group(1), ctx)))

  /** Recursively resolve `${{ ... }}` in every string leaf of a YAML-shaped value (Map / Seq / scalar). Non-string
    * leaves pass through unchanged. Returns Scala collections so Jackson can convert the result to a Cfg.
    */
  def resolveDeep(value: Any, ctx: Context): Any = value match {
    case s: String => resolve(s, ctx)
    case m: scala.collection.Map[_, _] =>
      m.iterator.map { case (k, v) => k.toString -> resolveDeep(v, ctx) }.toMap
    case jm: java.util.Map[_, _] =>
      jm.asScala.iterator.map { case (k, v) => k.toString -> resolveDeep(v, ctx) }.toMap
    case s: scala.collection.Iterable[_] => s.map(resolveDeep(_, ctx)).toSeq
    case jl: java.util.List[_]           => jl.asScala.map(resolveDeep(_, ctx)).toSeq
    case other                           => other
  }

  def evaluateBoolean(input: String, ctx: Context): Boolean = {
    val expr = stripBraces(input).trim
    expr match {
      case neqRe(l, r) => evalOperand(l, ctx) != evalOperand(r, ctx)
      case eqRe(l, r)  => evalOperand(l, ctx) == evalOperand(r, ctx)
      case _           => isTruthy(evalOperand(expr, ctx))
    }
  }

  private def stripBraces(s: String): String = {
    val t = s.trim
    if (t.startsWith("${{") && t.endsWith("}}")) t.substring(3, t.length - 2).trim else t
  }

  private def evalOperand(token: String, ctx: Context): String = {
    val t = token.trim
    if (
      t.length >= 2 && (
        (t.startsWith("'") && t.endsWith("'")) ||
          (t.startsWith("\"") && t.endsWith("\""))
      )
    ) {
      t.substring(1, t.length - 1)
    } else {
      lookup(t, ctx)
    }
  }

  private def isTruthy(s: String): Boolean = s.trim match {
    case "" | "false" | "0" => false
    case _                  => true
  }

  private def lookup(path: String, ctx: Context): String = path.split('.').toList match {
    case "env" :: key :: Nil =>
      ctx.env.getOrElse(EnvCascade.canonical(key), throw missing(s"env.$key"))

    case "needs" :: jobId :: "outputs" :: name :: Nil =>
      ctx.outputs
        .getOrElse(jobId, throw missing(s"needs.$jobId (job has no recorded outputs)"))
        .getOrElse(name, throw missing(s"needs.$jobId.outputs.$name"))

    case _ =>
      throw new IllegalArgumentException(s"Unsupported expression: '$path'")
  }

  private def missing(path: String): IllegalStateException =
    new IllegalStateException(s"Expression resolution failed: '$path' not found in context")
}

/** Runtime context maintained by the runner across job executions. `outputs` is keyed by jobId, then by emitted output
  * name.
  */
case class Context(
    env: Map[String, String] = Map.empty,
    outputs: Map[String, Map[String, String]] = Map.empty
)
