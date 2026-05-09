package com.kakao.actionbase.pipeline.jobs

import com.kakao.actionbase.pipeline.dsl.{Job, Plan}
import com.kakao.actionbase.pipeline.runner.StepsBuilder
import com.kakao.actionbase.pipeline.workflow.StepSpec

/** Generic Job whose Plan is built at runtime from an inline `steps:` list in its Cfg. Lets a workflow YAML express
  * a Source ~> Transform* ~> Sink chain without requiring a hand-written Job class:
  *
  * jobs: pi: uses: com.kakao.actionbase.pipeline.jobs.StepsRunnerJob with: steps:
  *   - uses: com.kakao.actionbase.pipeline.steps.source.SampleSource with: { n: 1000000, columns: [x, y] }
  *   - uses: com.kakao.actionbase.pipeline.steps.transform.SqlTransform with: { query: "SELECT ... FROM _0" }
  *   - uses: com.kakao.actionbase.pipeline.steps.sink.ShowSink
  */
case class StepsRunnerCfg(steps: Seq[StepSpec])

object StepsRunnerJob extends Job[StepsRunnerCfg] {
  override def plan(cfg: StepsRunnerCfg): Plan.Closed = StepsBuilder.build(cfg.steps)
}
