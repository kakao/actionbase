package com.kakao.actionbase.pipeline.workflow

import com.fasterxml.jackson.annotation.JsonProperty

/** Parsed YAML workflow spec. The Workflow itself is purely declarative — it describes a DAG of Jobs and how each is
  * parameterized. Runners (in-process EmbeddedRunner; production spark-submit-based — TBD) read this spec and
  * dispatch.
  *
  * YAML shape (mirrors GitHub Actions where it makes sense):
  *
  *   name: spark-pi
  *   schedule: '0 0 * * *'              # optional cron — workflow runs as a unit
  *   env: { SAMPLES: 1000000 }
  *   jobs:
  *     pi:
  *       uses: actionbase/pipeline@0.x:SparkPiJob
  *       with: { samples: "${{ env.SAMPLES }}" }
  *       submit: { preset: small }
  *
  * `schedule:` is a workflow-level cron expression (the workflow is itself a
  * complete DAG; its scheduling unit is the whole spec, not individual jobs).
  * EmbeddedRunner is a one-shot runner and only logs the value; production
  * runners (Airflow / GHA scheduled trigger / Jenkins cron) consume it.
  */
case class Workflow(
    name: String,
    schedule: Option[String] = None,
    env: Map[String, String] = Map.empty,
    presets: Map[String, Map[String, Any]] = Map.empty,
    jobs: Map[String, JobSpec]
)

/** `uses` — artifact + entry-point reference. GHA short form `actionbase/pipeline@0.x:SparkPiJob` or Maven coord
  * `com.kakao.actionbase:pipeline:0.x:SparkPiJob`; both resolve to the same `(group, artifact, version, kind,
  * mainClass)` via `UsesRef`. The trailing `:<class>` mirrors `spark-submit --class`. `needs` — ids of jobs that
  * must complete before this one starts. `if` — boolean expression; the job runs only when it evaluates to true
  * (defaults to true when absent). Cascades: if any `needs` was skipped, this job is skipped too. `with` — Cfg
  * fields for the entry-point class. Strings, numbers, booleans, lists, and nested maps are all allowed; string
  * leaves go through `${{ ... }}` resolution. `submit` — flat 1:1 map onto `spark-submit` CLI flags (kebab-case).
  * Required for `kind=spark`. Scalars become `--key value`; lists become `--key v1,v2`; maps (e.g. `conf:`) become
  * repeated `--key k=v`. Advisory in v1: EmbeddedRunner runs in-process and only logs them; production runners
  * (TBD) will assemble the actual `spark-submit` invocation.
  */
case class JobSpec(
    uses: String,
    needs: Seq[String] = Seq.empty,
    @JsonProperty("if") `if`: Option[String] = None,
    @JsonProperty("with") `with`: Map[String, Any] = Map.empty,
    submit: Map[String, Any] = Map.empty
)

/** A Step entry inside a Cfg whose Job interprets a `steps:` list (e.g., `StepsRunnerJob`).
  *
  * `step` — Step class name (Source / Transform / Sink); short / sub-package / FQN forms all resolve via
  * `ClassResolver.StepRoots`. `args` — constructor args bound to the Step's case-class fields. Both keys are
  * actionbase-internal — distinct from the GHA-style `uses:` / `with:` at the job level.
  *
  * Multi-input wiring:
  *   - `as` — optional label that names this step's output for downstream reference.
  *   - `inputs` — labels of upstream steps to feed in. Empty = the previous step's output (linear chain default).
  *     Multi-element = join semantics (e.g., `inputs: [users, events]` for a `SqlTransform` with two views).
  */
case class StepSpec(
    step: String,
    args: Map[String, Any] = Map.empty,
    as: Option[String] = None,
    inputs: Seq[String] = Seq.empty
)
