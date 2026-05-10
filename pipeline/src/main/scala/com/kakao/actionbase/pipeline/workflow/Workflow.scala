package com.kakao.actionbase.pipeline.workflow

import com.fasterxml.jackson.annotation.JsonProperty

/** Parsed workflow YAML.
  *
  * Job-to-job DAG. Each `jobs` entry is dispatched by its `kind:` to a runner-side handler. `presets:` is a free-form
  * reusable map referenced from elsewhere via `${{ presets.<name> }}`. `$extends` (a `${{ ... }}` expression that
  * resolves to a map) provides defaults via deep merge — see `ExtendsResolver`.
  */
case class Workflow(
    name: String,
    env: Map[String, String] = Map.empty,
    presets: Map[String, Map[String, Any]] = Map.empty,
    jobs: Map[String, JobSpec]
)

/** A single job in a workflow.
  *
  *   - `kind` — discriminator: `spark` | `bash`. Routes to a runner-side handler.
  *   - `artifact` — Gradle coord `group:name:version` (spark only). The runner resolves the JAR.
  *   - `mainClass` — class to invoke (spark only); short / sub-package / FQN forms all resolve via
  *     `ClassResolver.JobRoots`.
  *   - `args` — bound onto the Job's Cfg case class. Values may include `${{ ... }}` expressions.
  *   - `submit` — passed through to `spark-submit` CLI (kebab keys → flags). Nested `conf` map → `--conf` repeats.
  *   - `run` — shell command (bash only).
  *   - `needs` — ids of jobs that must complete before this one runs.
  *   - `when` — boolean expression; the job runs only when truthy. Defaults to true when absent.
  */
case class JobSpec(
    kind: String,
    artifact: Option[String] = None,
    mainClass: Option[String] = None,
    args: Map[String, Any] = Map.empty,
    submit: Map[String, Any] = Map.empty,
    run: Option[String] = None,
    needs: Seq[String] = Seq.empty,
    @JsonProperty("when") `when`: Option[String] = None
)
