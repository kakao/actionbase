package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.dsl._
import com.kakao.actionbase.pipeline.workflow.StepSpec

import scala.collection.mutable

/** Reflectively assembles a runnable `Plan.Closed` from a list of `StepSpec`s. Each spec's `args:` map is bound onto
  * the Step's case-class fields via Jackson; the resulting AST is wired together as a DAG.
  *
  * Wiring rules:
  *   - Source steps stand alone (no upstream).
  *   - A step's upstream inputs are resolved from `inputs:` labels first; if absent, the previous step's output is used
  *     (linear-chain default).
  *   - `as: <label>` registers the step's output for later `inputs:` references.
  *   - The terminal step must be a Sink; the result is a `Plan.Closed`.
  *
  * Expression resolution (`${{ ... }}`) is the caller's responsibility — `args:` values are bound as-is.
  */
object StepsBuilder {

  def build(steps: Seq[StepSpec]): Plan.Closed = {
    require(steps.nonEmpty, "`steps` must be non-empty")

    val byLabel   = mutable.LinkedHashMap.empty[String, Ast]
    val sinks     = mutable.ListBuffer.empty[Ast.Snk]
    var prev: Ast = null // most recent step's AST (linear-chain default upstream)

    steps.foreach { spec =>
      val instance = instantiate(spec)
      val ast: Ast = instance match {
        case s: Source =>
          require(spec.inputs.isEmpty, s"Source `${spec.step}` cannot declare `inputs:`")
          Ast.Src(s)

        case t: Transform =>
          Ast.Tx(resolveUpstreams(spec, byLabel, prev), t)

        case k: Sink =>
          require(
            spec.inputs.size <= 1,
            s"Sink `${spec.step}` is single-input; `inputs:` must have at most one entry, got ${spec.inputs.size}"
          )
          val (_, up) = resolveUpstreams(spec, byLabel, prev).head
          val snk     = Ast.Snk(up, k)
          sinks += snk
          snk

        case other =>
          throw new IllegalArgumentException(
            s"${spec.step} is not a Step (must extend Source, Transform, or Sink); got ${other.getClass.getName}"
          )
      }

      spec.as.foreach { label =>
        if (byLabel.contains(label)) {
          throw new IllegalArgumentException(s"duplicate step label `as: $label`")
        }
        byLabel(label) = ast
      }
      prev = ast
    }

    sinks.size match {
      case 0 => throw new IllegalArgumentException("workflow must have at least one Sink")
      case 1 => Plan.closed(sinks.head)
      case _ => Plan.closed(Ast.Group(sinks.toSeq))
    }
  }

  private def resolveUpstreams(
      spec: StepSpec,
      byLabel: collection.Map[String, Ast],
      prev: Ast
  ): Seq[(String, Ast)] = {
    val ups: Seq[(String, Ast)] =
      if (spec.inputs.nonEmpty) {
        spec.inputs.map { label =>
          val up = byLabel.getOrElse(
            label,
            throw new IllegalArgumentException(
              s"step `${spec.step}` references unknown input `$label`; " +
                s"known labels: ${if (byLabel.isEmpty) "<none>" else byLabel.keys.mkString(", ")}"
            )
          )
          label -> up
        }
      } else {
        if (prev == null) {
          throw new IllegalArgumentException(
            s"step `${spec.step}` has no upstream — the first step must be a Source, " +
              "or specify `inputs:` referencing labeled upstream(s)"
          )
        }
        Seq("_0" -> prev)
      }

    ups.foreach {
      case (_, _: Ast.Src) | (_, _: Ast.Tx) => // ok — produces a DataFrame
      case (_, other) =>
        throw new IllegalArgumentException(
          s"step `${spec.step}` upstream must be a Source or Transform, got ${describe(other)}"
        )
    }
    ups
  }

  private def instantiate(spec: StepSpec): Step = {
    val cls = ClassResolver.resolve(spec.step, ClassResolver.StepRoots)
    val obj = Job.mapper.convertValue(spec.args, cls)
    obj match {
      case step: Step => step
      case _ =>
        throw new IllegalArgumentException(
          s"${spec.step} is not a Step (must extend Source, Transform, or Sink)"
        )
    }
  }

  private def describe(ast: Ast): String = ast match {
    case Ast.Src(s)    => s"Source(${s.getClass.getSimpleName})"
    case Ast.Tx(_, t)  => s"Transform(${t.getClass.getSimpleName})"
    case Ast.Snk(_, s) => s"Sink(${s.getClass.getSimpleName})"
    case other         => other.toString
  }
}
