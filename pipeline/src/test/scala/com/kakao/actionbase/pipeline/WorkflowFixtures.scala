package com.kakao.actionbase.pipeline

import java.nio.file.{Files, Paths}

/** Shared test helper for resolving Workflow YAML files in `pipeline/workflows/`. The Gradle test working directory is
  * the module root (`pipeline/`), but tests started from the repo root also resolve correctly.
  */
object WorkflowFixtures {

  def path(name: String): String = {
    val candidates = Seq(
      Paths.get("workflows", name),
      Paths.get("pipeline", "workflows", name)
    )
    candidates
      .find(Files.exists(_))
      .map(_.toAbsolutePath.toString)
      .getOrElse(throw new IllegalStateException(s"Workflow YAML not found: $name"))
  }
}
