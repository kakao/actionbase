package com.kakao.actionbase.pipeline.dsl

import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable

// Type-state wrapper around the internal Ast.
//   Open   = chain ends in a Source or Transform; not yet runnable.
//   Closed = chain ends in a Sink (or Fork of Sinks); runnable.
sealed trait Plan {
  private[dsl] def ast: Ast
}

object Plan {

  final class Open private[dsl] (
      private[dsl] val ast: Ast,
      private[dsl] val edgeLabel: String = "_0"
  ) extends Plan {

    /** Name this step's output for the consumer's view of it (e.g., the temp view name a `SqlTransform` will see).
      * Defaults to `"_0"` if not called.
      */
    def as(label: String): Open = new Open(ast, label)

    def ~>(t: Transform): Open = new Open(Ast.Tx(Seq(edgeLabel -> ast), t))

    def ~>(s: Sink): Closed = new Closed(Ast.Snk(ast, s))

    /** Combine with another labeled output to feed a multi-input Transform:
      *
      * `(users.as("u") + events.as("e")) ~> SqlTransform("... FROM u JOIN e ...")`.
      */
    def +(other: Open): MultiOpen =
      new MultiOpen(Seq(edgeLabel -> ast, other.edgeLabel -> other.ast))

    def fanOut(branches: (Open => Closed)*): Closed = {
      require(branches.nonEmpty, "fanOut requires at least one branch")
      val branchAsts = branches.map { b =>
        val branchOpen = new Open(Ast.Ref)
        b(branchOpen).ast
      }
      new Closed(Ast.Fork(ast, branchAsts))
    }
  }

  /** Two or more labeled outputs combined for a multi-input Transform. Build via `Open.+`; finish with `~>`. */
  final class MultiOpen private[dsl] (private[dsl] val parts: Seq[(String, Ast)]) {
    def +(other: Open): MultiOpen = new MultiOpen(parts :+ (other.edgeLabel -> other.ast))
    def ~>(t: Transform): Open    = new Open(Ast.Tx(parts, t))
  }

  final class Closed private[dsl] (private[dsl] val ast: Ast) extends Plan {
    def run()(implicit spark: SparkSession): Unit = Executor.run(ast)
  }

  /** Escape hatch for runners that build the AST directly (e.g., `StepsBuilder` assembling a DAG from YAML). */
  private[pipeline] def closed(ast: Ast): Closed = new Closed(ast)
  private[pipeline] def open(ast: Ast): Open     = new Open(ast)
}

// Internal AST. Hidden from users so the DSL surface stays small.
private[pipeline] sealed trait Ast
private[pipeline] object Ast {
  case class Src(s: Source)                                  extends Ast
  case class Tx(upstreams: Seq[(String, Ast)], t: Transform) extends Ast
  case class Snk(upstream: Ast, s: Sink)                     extends Ast
  case class Fork(upstream: Ast, branches: Seq[Ast])         extends Ast
  // Multiple terminal roots executed under a shared materialization memo, so a common upstream is built once even
  // when several sinks consume it.
  case class Group(roots: Seq[Ast])                          extends Ast
  case object Ref                                            extends Ast
}

private[dsl] object Executor {

  def run(ast: Ast)(implicit spark: SparkSession): Unit = {
    val memo = mutable.Map.empty[Ast, DataFrame]
    runRoot(ast, memo)
  }

  private def runRoot(ast: Ast, memo: mutable.Map[Ast, DataFrame])(implicit
      spark: SparkSession
  ): Unit = ast match {
    case Ast.Snk(up, sink) =>
      sink.write(materialize(up, memo))

    case Ast.Fork(up, branches) =>
      val df = materialize(up, memo).cache()
      try branches.foreach(b => runBranch(b, df))
      finally df.unpersist(blocking = false)

    case Ast.Group(roots) =>
      roots.foreach(r => runRoot(r, memo))

    case other =>
      throw new IllegalStateException(
        s"Top-level Plan must end in a Sink, Fork, or Group, got: $other"
      )
  }

  private def materialize(ast: Ast, memo: mutable.Map[Ast, DataFrame])(implicit
      spark: SparkSession
  ): DataFrame = memo.getOrElseUpdate(
    ast,
    ast match {
      case Ast.Src(s) => s.read()
      case Ast.Tx(ups, t) =>
        t.apply(ups.map { case (label, up) => label -> materialize(up, memo) })
      case Ast.Ref =>
        throw new IllegalStateException("Ast.Ref outside fanOut branch — invalid Plan")
      case other =>
        throw new IllegalStateException(s"materialize cannot handle: $other")
    }
  )

  private def runBranch(ast: Ast, root: DataFrame)(implicit spark: SparkSession): Unit = {
    def go(p: Ast): DataFrame = p match {
      case Ast.Ref         => root
      case Ast.Tx(ups, t)  => t.apply(ups.map { case (label, up) => label -> go(up) })
      case other =>
        throw new IllegalStateException(
          s"fanOut branch must be Transform*-then-Sink rooted at the fork, got: $other"
        )
    }
    ast match {
      case Ast.Snk(up, sink) => sink.write(go(up))
      case other =>
        throw new IllegalStateException(s"fanOut branch must end in Sink, got: $other")
    }
  }
}
