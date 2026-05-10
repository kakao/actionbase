package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.runner.Expression.Context

import scala.collection.JavaConverters._

/** Resolves `$extends` directives in a parsed YAML tree.
  *
  * For every map containing `$extends:`:
  *   1. Evaluate the value (must be a single `${{ ... }}` expression that resolves to a map). 2. Recursively resolve
  *      `$extends` inside both the extended map and the surrounding sibling keys. 3. Deep-merge: the extended map
  *      provides defaults; the sibling map's keys override.
  *
  * `$` keys are reserved as processor directives — only `$extends` is recognized today; unknown `$<name>` keys are left
  * untouched (forward-compatible). Cycles are rejected via a depth limit.
  */
object ExtendsResolver {

  private val ExtendsKey = "$extends"
  private val MaxDepth   = 32
  private val WholeToken = """\A\s*\$\{\{\s*(.+?)\s*\}\}\s*\z""".r

  def resolve(value: Any, ctx: Context): Any = walk(value, ctx, depth = 0)

  private def walk(value: Any, ctx: Context, depth: Int): Any = value match {
    case m: java.util.Map[_, _] =>
      walkMap(m.asInstanceOf[java.util.Map[String, Any]].asScala.toMap, ctx, depth)
    case m: scala.collection.Map[_, _] =>
      walkMap(m.asInstanceOf[scala.collection.Map[String, Any]].toMap, ctx, depth)
    case l: java.util.List[_] =>
      l.asInstanceOf[java.util.List[Any]].asScala.map(walk(_, ctx, depth)).asJava
    case l: scala.collection.Iterable[_] =>
      l.map(walk(_, ctx, depth)).toSeq
    case other => other
  }

  private def walkMap(m: Map[String, Any], ctx: Context, depth: Int): java.util.Map[String, Any] = {
    if (depth > MaxDepth) throw new IllegalStateException(s"$$extends nesting exceeds $MaxDepth (cycle?)")

    m.get(ExtendsKey) match {
      case None =>
        m.map { case (k, v) => k -> walk(v, ctx, depth) }.toMap.asJava

      case Some(expr) =>
        val incoming = evalExtendsValue(expr, ctx)
        val resolvedIncoming = walk(incoming, ctx, depth + 1) match {
          case jm: java.util.Map[_, _] => jm.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
          case other =>
            throw new IllegalArgumentException(s"$$extends must resolve to a map, got: ${other.getClass.getSimpleName}")
        }
        val sibling = (m - ExtendsKey).map { case (k, v) => k -> walk(v, ctx, depth) }
        deepMerge(resolvedIncoming, sibling).asJava
    }
  }

  private def evalExtendsValue(expr: Any, ctx: Context): Any = expr match {
    case s: String =>
      s.trim match {
        case WholeToken(inner) => Expression.evaluate(inner, ctx)
        case other =>
          throw new IllegalArgumentException(
            s"$$extends value must be a single `\\$${{ ... }}` expression, got: $other"
          )
      }
    case other =>
      throw new IllegalArgumentException(s"$$extends value must be a string expression, got: $other")
  }

  /** Recursive deep-merge: maps merge key-by-key (sibling wins on conflict at non-map leaves); other values are
    * replaced wholesale by the sibling.
    */
  private def deepMerge(base: Map[String, Any], over: Map[String, Any]): Map[String, Any] = {
    val keys = base.keySet ++ over.keySet
    keys.iterator.map { k =>
      (base.get(k), over.get(k)) match {
        case (Some(a), Some(b)) =>
          k -> mergeValue(a, b)
        case (Some(a), None) => k -> a
        case (None, Some(b)) => k -> b
        case _               => sys.error("unreachable")
      }
    }.toMap
  }

  private def mergeValue(a: Any, b: Any): Any = (a, b) match {
    case (am: java.util.Map[_, _], bm: java.util.Map[_, _]) =>
      deepMerge(
        am.asInstanceOf[java.util.Map[String, Any]].asScala.toMap,
        bm.asInstanceOf[java.util.Map[String, Any]].asScala.toMap
      ).asJava
    case _ => b
  }
}
