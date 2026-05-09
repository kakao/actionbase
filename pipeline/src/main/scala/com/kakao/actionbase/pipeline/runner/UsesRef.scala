package com.kakao.actionbase.pipeline.runner

/** Parsed `uses:` reference. Two surface forms collapse into the same Maven coord; only the group portion differs:
  *
  *   - GHA-style sugar: `<owner>/<artifact>@<version>[:<class>]` — `<owner>` is a registered alias for a Maven
  *     groupId. The optional trailing `:<class>` names the entry point (mirrors `spark-submit --class`).
  *   - Maven coord: `<group>:<artifact>:<version>[:<class>]` — used directly.
  *
  * Owner aliases (v1): `actionbase` → `com.kakao.actionbase`. So the following are equivalent:
  *
  *   `actionbase/pipeline@0.x:SparkPiJob`     ≡ `com.kakao.actionbase:pipeline:0.x:SparkPiJob`
  *   `actionbase/pipeline@0.3.0-SNAPSHOT:Foo` ≡ `com.kakao.actionbase:pipeline:0.3.0-SNAPSHOT:Foo`
  *
  * `<version>` is a free-form string passed to the runner. EmbeddedRunner is in-process and never fetches a jar, so
  * it ignores the version entirely. Production runners (TBD) will resolve it via Maven repo metadata. Recommended
  * forms (npm-/semver-style, in increasing strictness): `0.x` (any 0.* release), `0.3.x`, `0.3.0-SNAPSHOT`. `latest`
  * is accepted but discouraged — it can pull a major-version-incompatible release.
  *
  * `kind` selects the runtime dispatch; v1 supports `spark` only and defaults to it.
  */
case class UsesRef(
    group: String,
    artifact: String,
    version: String,
    kind: String,
    mainClass: Option[String]
) {
  def coord: String = s"$group:$artifact:$version"
}

object UsesRef {

  /** Short-owner → Maven groupId aliases. Add an entry to expose a new short prefix. */
  private val OwnerAliases: Map[String, String] = Map(
    "actionbase" -> "com.kakao.actionbase"
  )

  private val GhaRe   = """^([\w.-]+)/([\w.-]+)@([^:]+?)(?::([\w.]+))?$""".r
  private val MavenRe = """^([\w.]+):([\w.-]+):([^:]+?)(?::([\w.]+))?$""".r

  def parse(s: String): UsesRef = s match {
    case GhaRe(owner, artifact, version, klass) =>
      val group = OwnerAliases.getOrElse(
        owner,
        throw new IllegalArgumentException(
          s"Unknown owner alias `$owner`. Known: ${OwnerAliases.keys.mkString(", ")}"
        )
      )
      UsesRef(group, artifact, version, "spark", Option(klass))
    case MavenRe(group, artifact, version, klass) =>
      UsesRef(group, artifact, version, "spark", Option(klass))
    case other =>
      throw new IllegalArgumentException(
        s"`uses:` must be `<owner>/<artifact>@<version>[:<class>]` or " +
          s"`<group>:<artifact>:<version>[:<class>]`, got: '$other'"
      )
  }
}
