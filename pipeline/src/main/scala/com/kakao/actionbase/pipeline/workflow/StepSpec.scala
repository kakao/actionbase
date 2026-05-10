package com.kakao.actionbase.pipeline.workflow

/** A Step entry inside a Cfg whose Job interprets a `steps:` list (e.g., `StepsRunnerJob`).
  *
  *   - `step` — Step class name (Source / Transform / Sink); short, sub-package, and FQN forms all resolve via
  *     `ClassResolver.StepRoots`.
  *   - `args` — constructor args bound to the Step's case-class fields.
  *   - `as` — optional label that names this step's output for downstream reference.
  *   - `inputs` — labels of upstream steps to feed in. Empty = the previous step's output (linear-chain default, label
  *     `"_0"`). Multiple = join semantics (e.g., `inputs: [users, events]` for a `SqlTransform` with two views).
  */
case class StepSpec(
    step: String,
    args: Map[String, Any] = Map.empty,
    as: Option[String] = None,
    inputs: Seq[String] = Seq.empty
)
