package com.kakao.actionbase.pipeline.dsl

import org.apache.spark.sql.{DataFrame, SparkSession}

import java.util.IdentityHashMap

// Type-state: Open = at a single output. Forked = at a Split's M outputs. Closed = ends in Sink/Fork/Group (runnable).
sealed trait Plan {
  private[dsl] def ast: Ast
}

object Plan {

  final class Open private[dsl] (
      private[dsl] val ast: Ast,
      private[dsl] val edgeLabel: String = DefaultLabel
  ) extends Plan {

    // Label this output for downstream `as:` references (e.g., a SqlTransform temp view). Defaults to "_0".
    def as(label: String): Open = new Open(ast, label)

    def ~>(f: Flow): Open     = new Open(Ast.Fl(ast, f))
    def ~>(m: Merge): Open    = new Open(Ast.Mg(Seq(edgeLabel -> ast), m))
    def ~>(sp: Split): Forked = new Forked(Ast.Sp(ast, sp))
    def ~>(s: Sink): Closed   = new Closed(Ast.Snk(ast, s))

    def +(other: Open): MultiOpen =
      new MultiOpen(Seq(edgeLabel -> ast, other.edgeLabel -> other.ast))

    def fanOut(branches: (Open => Closed)*): Closed = {
      require(branches.nonEmpty, "fanOut requires at least one branch")
      val branchAsts = branches.map { b =>
        val closed = b(new Open(Ast.Ref))
        PlanValidation.validateBranch(closed.ast)
        closed.ast
      }
      new Closed(Ast.Fork(ast, branchAsts))
    }
  }

  final class MultiOpen private[dsl] (private[dsl] val parts: Seq[(String, Ast)]) {
    def +(other: Open): MultiOpen = new MultiOpen(parts :+ (other.edgeLabel -> other.ast))
    def ~>(m: Merge): Open        = new Open(Ast.Mg(parts, m))
  }

  final class Forked private[dsl] (private[dsl] val splitAst: Ast.Sp) {
    def apply(port: String): Open = {
      require(splitAst.sp.ports.contains(port), s"unknown port '$port'; known: ${splitAst.sp.ports.mkString(", ")}")
      new Open(Ast.Port(splitAst, port))
    }
    def ports: Seq[String] = splitAst.sp.ports
  }

  final class Closed private[dsl] (private[dsl] val ast: Ast) extends Plan {
    def run()(implicit spark: SparkSession): Unit = {
      PlanValidation.validate(ast)
      Executor.run(ast)
    }
  }

  // Multiple Closeds share an execution memo, so a common upstream materializes once.
  def bundle(closeds: Closed*): Closed = {
    require(closeds.nonEmpty, "bundle requires at least one Closed")
    if (closeds.size == 1) closeds.head
    else new Closed(Ast.Group(closeds.map(_.ast)))
  }

  // Escape hatches for runners assembling the AST directly (e.g., StepsBuilder).
  private[pipeline] def closed(ast: Ast): Closed = new Closed(ast)
  private[pipeline] def open(ast: Ast): Open     = new Open(ast)
}

private[pipeline] sealed trait Ast
private[pipeline] object Ast {
  case class Src(s: Source)                              extends Ast
  case class Fl(upstream: Ast, f: Flow)                  extends Ast
  case class Mg(upstreams: Seq[(String, Ast)], m: Merge) extends Ast
  case class Sp(upstream: Ast, sp: Split)                extends Ast
  case class Port(splitNode: Sp, port: String)           extends Ast
  case class Snk(upstream: Ast, s: Sink)                 extends Ast
  case class Fork(upstream: Ast, branches: Seq[Ast])     extends Ast
  case class Group(roots: Seq[Ast])                      extends Ast
  // Placeholder for a fanOut branch's shared upstream; valid only inside a branch, where the Executor seeds the
  // memo with Ref → the cached root. PlanValidation.validateBranch keeps it from escaping that context.
  case object Ref extends Ast
}

private[dsl] object Executor {

  // Identity-keyed: a shared upstream node materializes once; two separately-built Sources stay independent.
  private type Memo      = IdentityHashMap[Ast, DataFrame]
  private type SplitMemo = IdentityHashMap[Ast.Sp, Map[String, DataFrame]]

  def run(ast: Ast)(implicit spark: SparkSession): Unit = runRoot(ast, new Memo(), new SplitMemo())

  private def runRoot(ast: Ast, memo: Memo, splitMemo: SplitMemo)(implicit spark: SparkSession): Unit = ast match {
    case Ast.Snk(up, sink) => sink.write(materialize(up, memo, splitMemo))
    case Ast.Group(roots)  => roots.foreach(r => runRoot(r, memo, splitMemo))
    case Ast.Fork(up, branches) =>
      val df = materialize(up, memo, splitMemo).cache()
      // Non-blocking: blocking would serialize against executor RPCs for no real benefit at job-termination.
      try branches.foreach(runBranch(_, df))
      finally df.unpersist(blocking = false)
    case other => throw new IllegalStateException(s"Top-level Plan must end in a Sink, Fork, or Group, got: $other")
  }

  private def materialize(ast: Ast, memo: Memo, splitMemo: SplitMemo)(implicit spark: SparkSession): DataFrame =
    memo.computeIfAbsent(
      ast,
      _ =>
        ast match {
          case Ast.Src(s)    => s.read()
          case Ast.Fl(up, f) => f.apply(materialize(up, memo, splitMemo))
          case Ast.Mg(ups, m) =>
            m.apply(ups.map { case (label, up) => label -> materialize(up, memo, splitMemo) })
          case Ast.Port(sp, port) =>
            val splitMap = splitMemo.computeIfAbsent(sp, _ => sp.sp.split(materialize(sp.upstream, memo, splitMemo)))
            splitMap.getOrElse(
              port,
              throw new IllegalStateException(s"unknown port '$port'; got: ${splitMap.keys.mkString(", ")}")
            )
          case Ast.Ref => throw new IllegalStateException("Ast.Ref outside fanOut branch — invalid Plan")
          case other   => throw new IllegalStateException(s"materialize cannot handle: $other")
        }
    )

  // Branches share the fork's cached root via a pre-seeded memo (Ast.Ref → root).
  private def runBranch(ast: Ast, root: DataFrame)(implicit spark: SparkSession): Unit = ast match {
    case Ast.Snk(up, sink) =>
      val memo = new Memo()
      memo.put(Ast.Ref, root)
      sink.write(materialize(up, memo, new SplitMemo()))
    case other => throw new IllegalStateException(s"fanOut branch must end in Sink, got: $other")
  }
}
