package com.kakao.actionbase.pipeline.workflow

// `as` is `Any` so YAML can supply either a string (single output label) or a {port: label} map for a Split.
case class StepSpec(
    step: String,
    args: Map[String, Any] = Map.empty,
    as: Option[Any] = None,
    inputs: Seq[String] = Seq.empty
)
