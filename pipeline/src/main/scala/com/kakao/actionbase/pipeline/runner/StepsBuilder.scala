package com.kakao.actionbase.pipeline.runner

import com.kakao.actionbase.pipeline.dsl._
import com.kakao.actionbase.pipeline.workflow.StepSpec

import scala.collection.JavaConverters._
import scala.collection.mutable

// Assembles a runnable Plan.Closed from a list of StepSpecs. `inputs:` resolves by label; absent = previous step
// (linear-chain default, label "_0"). A Split has no canonical "next", so downstream must reference port labels.
object StepsBuilder {

  def build(steps: Seq[StepSpec]): Plan.Closed = {
    require(steps.nonEmpty, "`steps` must be non-empty")

    val byLabel           = mutable.LinkedHashMap.empty[String, Ast]
    val sinks             = mutable.ListBuffer.empty[Ast.Snk]
    var prev: Option[Ast] = None

    steps.foreach { spec =>
      val instance = instantiate(spec)
      val ast: Ast = instance match {
        case s: Source =>
          require(spec.inputs.isEmpty, s"Source `${spec.step}` cannot declare `inputs:`")
          Ast.Src(s)
        case f: Flow =>
          val ups = resolveUpstreams(spec, byLabel, prev)
          require(ups.size == 1, s"Flow `${spec.step}` is single-input; got ${ups.size}")
          Ast.Fl(ups.head._2, f)
        case m: Merge =>
          Ast.Mg(resolveUpstreams(spec, byLabel, prev), m)
        case sp: Split =>
          val ups = resolveUpstreams(spec, byLabel, prev)
          require(ups.size == 1, s"Split `${spec.step}` is single-input; got ${ups.size}")
          Ast.Sp(ups.head._2, sp)
        case k: Sink =>
          require(spec.as.isEmpty, s"Sink `${spec.step}` cannot declare `as:`")
          require(spec.inputs.size <= 1, s"Sink `${spec.step}` is single-input; got ${spec.inputs.size} inputs")
          val snk = Ast.Snk(resolveUpstreams(spec, byLabel, prev).head._2, k)
          sinks += snk
          snk
        case other =>
          throw new IllegalArgumentException(s"${spec.step} is not a Step; got ${other.getClass.getName}")
      }
      registerLabels(spec, instance, ast, byLabel)
      prev = instance match {
        case _: Split => None
        case _: Sink  => prev
        case _        => Some(ast)
      }
    }

    sinks.size match {
      case 0 => throw new IllegalArgumentException("workflow must have at least one Sink")
      case 1 => Plan.closed(sinks.head)
      case _ => Plan.closed(Ast.Group(sinks.toSeq))
    }
  }

  private def registerLabels(
      spec: StepSpec,
      instance: Step,
      ast: Ast,
      byLabel: mutable.LinkedHashMap[String, Ast]
  ): Unit = spec.as match {
    case None | Some(null) =>
    case Some(label: String) =>
      require(!instance.isInstanceOf[Split], s"Split `${spec.step}` requires `as: {port: label}` map form")
      addLabel(byLabel, label, ast, spec)
    case Some(jmap: java.util.Map[_, _]) =>
      addPortLabels(
        spec,
        instance,
        ast,
        byLabel,
        jmap.asScala.iterator.map { case (k, v) => k.toString -> v.toString }.toMap
      )
    case Some(smap: Map[_, _] @unchecked) =>
      addPortLabels(spec, instance, ast, byLabel, smap.map { case (k, v) => k.toString -> v.toString })
    case Some(other) =>
      throw new IllegalArgumentException(
        s"step `${spec.step}`: `as:` must be a string or {port: label} map, got ${other.getClass.getName}"
      )
  }

  private def addPortLabels(
      spec: StepSpec,
      instance: Step,
      ast: Ast,
      byLabel: mutable.LinkedHashMap[String, Ast],
      portLabels: Map[String, String]
  ): Unit = (instance, ast) match {
    case (sp: Split, spAst: Ast.Sp) =>
      portLabels.keys.foreach { port =>
        require(
          sp.ports.contains(port),
          s"Split `${spec.step}` has no port `$port`; declared: ${sp.ports.mkString(", ")}"
        )
      }
      // Every declared port must be labeled, else its rows are silently dropped — almost always a wiring mistake.
      val unlabeled = sp.ports.filterNot(portLabels.contains)
      require(
        unlabeled.isEmpty,
        s"Split `${spec.step}` leaves port(s) ${unlabeled.mkString(", ")} unlabeled; map every declared port"
      )
      portLabels.foreach { case (port, label) => addLabel(byLabel, label, Ast.Port(spAst, port), spec) }
    case _ =>
      throw new IllegalArgumentException(
        s"step `${spec.step}` is not a Split; `as: {port: label}` map form is only for Split"
      )
  }

  private def addLabel(byLabel: mutable.LinkedHashMap[String, Ast], label: String, ast: Ast, spec: StepSpec): Unit = {
    require(!byLabel.contains(label), s"duplicate step label `as: $label` (declared by `${spec.step}`)")
    byLabel(label) = ast
  }

  private def resolveUpstreams(
      spec: StepSpec,
      byLabel: collection.Map[String, Ast],
      prev: Option[Ast]
  ): Seq[(String, Ast)] = {
    val ups: Seq[(String, Ast)] =
      if (spec.inputs.nonEmpty)
        spec.inputs.map { label =>
          val known = if (byLabel.isEmpty) "<none>" else byLabel.keys.mkString(", ")
          label -> byLabel.getOrElse(
            label,
            throw new IllegalArgumentException(s"step `${spec.step}` references unknown input `$label`; known: $known")
          )
        }
      else
        Seq(
          DefaultLabel -> prev.getOrElse(
            throw new IllegalArgumentException(
              s"step `${spec.step}` has no upstream — first step must be a Source, or specify `inputs:`"
            )
          )
        )

    ups.foreach {
      case (_, _: Ast.Src | _: Ast.Fl | _: Ast.Mg | _: Ast.Port) =>
      case (_, other) =>
        throw new IllegalArgumentException(
          s"step `${spec.step}` upstream must produce a DataFrame, got ${other.getClass.getSimpleName}"
        )
    }
    ups
  }

  private def instantiate(spec: StepSpec): Step = {
    val cls = ClassResolver.resolve(spec.step, ClassResolver.StepRoots)
    // Reject non-Step FQNs before `<clinit>` runs (see ClassResolver).
    require(classOf[Step].isAssignableFrom(cls), s"${spec.step} is not a Step; got ${cls.getName}")
    try Job.stepMapper.convertValue(spec.args, cls).asInstanceOf[Step]
    catch {
      case e: RuntimeException =>
        throw new IllegalArgumentException(
          s"failed to bind args for step `${spec.step}` (args=${spec.args}): ${e.getMessage}",
          e
        )
    }
  }
}
