package com.kakao.actionbase.pipeline.workflow

import com.fasterxml.jackson.annotation.JsonProperty

/** Minimal GHA workflow schema — just the bits we read.
  *
  * The user-facing YAML is a real GitHub Actions workflow. Our `WorkflowLoader` parses it into this shape and then
  * adapts to the internal `Workflow` / `JobSpec` model used by the runner. Fields irrelevant to actionbase
  * (`runs-on`, `permissions`, etc.) are kept as `Any` or ignored, but tolerated so the file remains valid for GHA's
  * own parser.
  */
case class GhaWorkflow(
    name: String,
    // GHA accepts `on:` as a String (`on: push`), an array (`on: [push, pull_request]`), or a map
    // (`on: { workflow_dispatch: {}, schedule: [...] }`). Keep it untyped and pull `schedule` out by hand.
    on: Option[Any] = None,
    env: Map[String, String] = Map.empty,
    presets: Map[String, Map[String, Any]] = Map.empty,
    jobs: Map[String, GhaJob]
)

case class GhaJob(
    @JsonProperty("runs-on") runsOn: String = "ubuntu-latest",
    needs: Seq[String] = Seq.empty,
    @JsonProperty("if") `if`: Option[String] = None,
    env: Map[String, String] = Map.empty,
    steps: Seq[GhaStep]
)

case class GhaStep(
    uses: Option[String] = None,
    name: Option[String] = None,
    @JsonProperty("if") `if`: Option[String] = None,
    @JsonProperty("with") `with`: Map[String, Any] = Map.empty,
    run: Option[String] = None,
    env: Map[String, String] = Map.empty
)
