package com.kakao.actionbase.pipeline.jobs

import com.kakao.actionbase.pipeline.dsl.{Job, Plan}
import com.kakao.actionbase.pipeline.runner.StepsBuilder
import com.kakao.actionbase.pipeline.workflow.StepSpec

/** Generic Job whose Plan is built at runtime from an inline `steps:` list in its Cfg. Lets a workflow YAML express a
  * Source ~> Transform* ~> Sink chain without requiring a hand-written Job class:
  *
  * {{{
  * jobs:
  *   pi:
  *     kind: spark
  *     artifact: "com.kakao.actionbase:pipeline:0.x"
  *     mainClass: StepsRunnerJob
  *     args:
  *       steps:
  *         - step: SampleSource
  *           args: { n: 1000000, columns: [x, y] }
  *         - step: SqlTransform
  *           args: { query: "SELECT ... FROM _0" }
  *         - step: ShowSink
  * }}}
  */
case class StepsRunnerCfg(steps: Seq[StepSpec])

object StepsRunnerJob extends Job[StepsRunnerCfg] {
  override def plan(cfg: StepsRunnerCfg): Plan.Closed = StepsBuilder.build(cfg.steps)
}
