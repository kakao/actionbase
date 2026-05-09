package com.kakao.actionbase.pipeline.runner

/** Resolves a job's `submit:` block against the workflow's `presets:` table.
  *
  * If `submit` declares `preset: <name>`, the named preset is used as a base and the rest of `submit` is layered on
  * top:
  *
  *   - top-level keys present in user `submit` replace preset values,
  *   - nested maps (e.g., `conf:`) merge deeply per-key,
  *   - lists and scalars are replaced wholesale.
  */
object SubmitPreset {

  def resolve(
      submit: Map[String, Any],
      presets: Map[String, Map[String, Any]]
  ): Map[String, Any] = submit.get("preset") match {
    case None => submit
    case Some(name: String) =>
      val base = presets.getOrElse(
        name,
        throw new IllegalArgumentException(
          s"Unknown submit preset `$name`. Known: ${if (presets.isEmpty) "<none>" else presets.keys.mkString(", ")}"
        )
      )
      deepMerge(base, submit - "preset")
    case Some(other) =>
      throw new IllegalArgumentException(s"`submit.preset:` must be a string, got: $other")
  }

  private def deepMerge(base: Map[String, Any], overrides: Map[String, Any]): Map[String, Any] =
    base ++ overrides.map { case (k, v) =>
      (base.get(k), v) match {
        case (Some(b: Map[_, _]), o: Map[_, _]) =>
          k -> deepMerge(b.asInstanceOf[Map[String, Any]], o.asInstanceOf[Map[String, Any]])
        case _ => k -> v
      }
    }
}
