package com.kakao.actionbase.pipeline.jobs

import com.kakao.actionbase.pipeline.dsl.{Job, Plan}
import com.kakao.actionbase.pipeline.runner.StepsBuilder
import com.kakao.actionbase.pipeline.workflow.StepSpec

// Job whose Plan is assembled from a YAML `steps:` list — express a Source~>...~>Sink chain without writing a Job class.
case class StepsRunnerCfg(steps: Seq[StepSpec])

object StepsRunnerJob extends Job[StepsRunnerCfg] {
  override def plan(cfg: StepsRunnerCfg): Plan.Closed = StepsBuilder.build(cfg.steps)
}
