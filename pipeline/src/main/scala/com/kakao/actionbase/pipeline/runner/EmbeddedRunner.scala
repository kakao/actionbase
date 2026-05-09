package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.dsl.Job
import com.kakao.actionbase.pipeline.runtime.JobOutputs
import com.kakao.actionbase.pipeline.workflow.{JobSpec, Workflow, WorkflowLoader}
import org.apache.spark.sql.SparkSession

import java.nio.file.Files

import scala.collection.mutable

/** In-process runner for a Workflow YAML. Resolves dependencies via topological sort and invokes each Job in the
  * same JVM. Each Job creates and tears down its own SparkSession (`local[*]` by default).
  *
  * Intended for tests and local dev: no `spark-submit` binary is required and `submit:` is advisory. For
  * production-parity execution (subprocess `spark-submit`, `submit:` mapped to CLI flags), use a `spark-submit`-based
  * runner — TBD.
  *
  * Expression resolution: `${{ env.X }}` and `${{ needs.A.outputs.X }}` are substituted in every string leaf of each
  * Job's `with:` map (recursively into nested maps and lists) before binding to the Job's Cfg.
  */
object EmbeddedRunner {

  def main(argv: Array[String]): Unit = {
    require(argv.nonEmpty, "Usage: EmbeddedRunner <workflow.yaml> [KEY=VALUE ...]")
    val workflowPath = argv.head
    val cliArgs = argv.tail.flatMap { kv =>
      kv.split("=", 2) match {
        case Array(k, v) if k.nonEmpty => Some(k -> v)
        case _ =>
          System.err.println(s"[EmbeddedRunner] ignoring malformed env override: '$kv' (expected KEY=VALUE)")
          None
      }
    }.toMap

    val wf        = WorkflowLoader.load(workflowPath)
    val perSource = EnvCascade.perSource(wf.env, cliArgs)
    EnvCascade.printReport(perSource)

    run(wf, cliArgs)
  }

  def run(
      wf: Workflow,
      extraEnv: Map[String, String] = Map.empty
  ): Map[String, Map[String, String]] = {
    val order = topoSort(wf.jobs)
    val sched = wf.schedule.map(s => s" (schedule advisory: '$s')").getOrElse("")
    println(s"Workflow '${wf.name}'$sched — running ${order.size} job(s): ${order.mkString(" -> ")}")

    val tmpDir  = Files.createTempDirectory(s"actionbase-runner-${wf.name}-")
    val canonEnv = EnvCascade.merged(EnvCascade.perSource(wf.env, extraEnv))
    var ctx      = Context(env = canonEnv)
    val skipped  = mutable.Set[String]()

    order.foreach { id =>
      val spec        = wf.jobs(id)
      val skippedDeps = spec.needs.filter(skipped.contains)

      if (skippedDeps.nonEmpty) {
        println(s"--- [$id] SKIPPED (dep skipped: ${skippedDeps.mkString(", ")})")
        skipped += id
      } else if (!spec.`if`.forall(cond => Expression.evaluateBoolean(cond, ctx))) {
        println(s"--- [$id] SKIPPED (if='${spec.`if`.get}')")
        skipped += id
      } else {
        val ref             = UsesRef.parse(spec.uses)
        val effectiveSubmit = SubmitPreset.resolve(spec.submit, wf.presets)

        if (ref.kind == "spark" && effectiveSubmit.isEmpty) {
          throw new IllegalArgumentException(
            s"[$id] `submit:` is required for kind=spark — even though EmbeddedRunner runs in-process, " +
              "production runners need it to assemble `spark-submit`. Use `preset:` or inline keys."
          )
        }

        val outputFile = tmpDir.resolve(s"$id.outputs").toString
        System.setProperty(JobOutputs.SystemPropertyKey, outputFile)

        if (effectiveSubmit.nonEmpty) {
          println(s"    [$id] submit (advisory, in-process runner ignores): $effectiveSubmit")
        }

        try runJob(id, spec, ctx, ref)
        finally System.clearProperty(JobOutputs.SystemPropertyKey)

        val outputs = JobOutputs.read(outputFile)
        if (outputs.nonEmpty) println(s"    [$id] outputs: $outputs")
        ctx = ctx.copy(outputs = ctx.outputs + (id -> outputs))
      }
    }
    ctx.outputs
  }

  private def runJob(id: String, spec: JobSpec, ctx: Context, ref: UsesRef): Unit = {
    val resolvedWith = Expression.resolveDeep(spec.`with`, ctx).asInstanceOf[Map[String, Any]]

    println(s"    [$id] uses ${ref.coord} (kind=${ref.kind})")

    ref.kind match {
      case "spark" => runSparkJob(id, ref, resolvedWith)
      case other =>
        throw new IllegalArgumentException(
          s"[$id] kind '$other' is reserved for future use; v1 supports `spark` only"
        )
    }
  }

  private def runSparkJob(id: String, ref: UsesRef, resolvedWith: Map[String, Any]): Unit = {
    val className = ref.mainClass.getOrElse {
      throw new IllegalArgumentException(
        s"[$id] `uses:` must include the entry-point class — " +
          s"e.g., `${ref.coord}:MyJob` (mirrors `spark-submit --class`)"
      )
    }

    println(s"--- [$id] $className $resolvedWith")
    val plan  = loadJob(className).planFromMap(resolvedWith)
    val spark = SparkSession.builder().appName(s"actionbase-$id").getOrCreate()
    try plan.run()(spark)
    finally spark.stop()
  }

  // Kahn-style DFS topo sort. Detects cycles, fails on unknown `needs`.
  private def topoSort(jobs: Map[String, JobSpec]): Seq[String] = {
    val visited  = mutable.LinkedHashSet[String]()
    val visiting = mutable.Set[String]()

    def visit(id: String): Unit = {
      if (visiting(id)) throw new IllegalStateException(s"Cycle detected involving job '$id'")
      if (!visited(id)) {
        visiting += id
        val spec = jobs.getOrElse(
          id,
          throw new IllegalStateException(s"Unknown job in 'needs': '$id'")
        )
        spec.needs.foreach(visit)
        visiting -= id
        visited += id
      }
    }

    jobs.keys.toSeq.sorted.foreach(visit)
    visited.toSeq
  }

  // Scala objects compile to a class with a static `MODULE$` field — load it
  // and cast back to the Job contract. `name` may be a full FQN, a short name,
  // or a sub-package name relative to `ClassResolver.JobRoots`.
  private def loadJob(name: String): Job[_ <: Product] = {
    val cls    = ClassResolver.resolve(s"$name$$", ClassResolver.JobRoots)
    val module = cls.getField("MODULE$").get(null)
    module.asInstanceOf[Job[_ <: Product]]
  }
}
