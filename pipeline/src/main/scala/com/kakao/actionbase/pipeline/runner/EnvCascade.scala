package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.util.ConfigSource

/** Cascading resolution of a workflow's `env:` overrides, mirroring the `ConfigLoader` / `ConfigPrinter` model used
  * by Job Cfg loading. Sources are merged left-to-right, so later wins. v1 ships two sources:
  *
  *   yaml (workflow `env:` defaults) < args (CLI `KEY=VALUE` overrides)
  *
  * Keys are matched Spring-relaxed-binding style: every key is canonicalized to lowercase with `--` / `-` / `_`
  * stripped, so `SAMPLES`, `samples`, `--samples`, `--Sample-S`, and `sample_s` all resolve to the same entry.
  */
object EnvCascade {

  /** Canonical key form: `lower_snake_case`. The same canonical key matches across these surface forms:
    *
    *   `SOME_SAMPLES` ≡ `some_samples` ≡ `someSamples` ≡ `some-samples` ≡ `--some-samples`
    *
    * The rule: drop a leading `--`, swap `-` for `_`, insert `_` at every camel-case boundary, lowercase. Word
    * boundaries inside the key are preserved — `sample_s` stays distinct from `samples`, so distinct logical names
    * don't accidentally collide.
    */
  def canonical(k: String): String = {
    val stripped = if (k.startsWith("--")) k.drop(2) else k
    stripped
      .replace('-', '_')
      .replaceAll("([a-z])([A-Z])", "$1_$2")
      .toLowerCase
  }

  case class YamlSource(env: Map[String, String]) extends ConfigSource {
    val name                          = "yaml"
    def load(): Map[String, String]   = env.map { case (k, v) => canonical(k) -> v }
  }

  case class ArgsSource(args: Map[String, String]) extends ConfigSource {
    val name                          = "args"
    def load(): Map[String, String]   = args.map { case (k, v) => canonical(k) -> v }
  }

  def perSource(yamlEnv: Map[String, String], cliArgs: Map[String, String]): Seq[(String, Map[String, String])] = {
    val sources: Seq[ConfigSource] = Seq(YamlSource(yamlEnv), ArgsSource(cliArgs))
    sources.map(s => s.name -> s.load())
  }

  def merged(perSource: Seq[(String, Map[String, String])]): Map[String, String] =
    if (perSource.isEmpty) Map.empty else perSource.map(_._2).reduce(_ ++ _)

  /** Per-key report of the resolved env: winning value, its source, and a trace of every source that contributed.
    * Matches `ConfigPrinter.printConfigReport` shape so a reader sees the same format across Cfg and env loading.
    */
  def printReport(perSource: Seq[(String, Map[String, String])]): Unit = {
    val m = merged(perSource)
    if (m.isEmpty) return
    println("=== Workflow env ===")
    m.keys.toSeq.sorted.foreach { k =>
      val v = m(k)
      val origin = perSource.reverse
        .collectFirst { case (n, src) if src.contains(k) => n }
        .getOrElse("?")
      val trace     = perSource.flatMap { case (n, src) => src.get(k).map(vv => s"$n=$vv") }
      val tracePart = if (trace.isEmpty) "" else s"  (${trace.mkString(", ")})"
      println(s"  $k = $v  [$origin]$tracePart")
    }
  }
}
