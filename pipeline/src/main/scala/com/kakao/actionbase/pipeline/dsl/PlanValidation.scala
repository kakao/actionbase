package com.kakao.actionbase.pipeline.dsl

// Structural plan-time checks shared by both front-ends: the DSL runs `validate` from `Closed.run` and reuses
// `validateBranch` for eager `fanOut` errors; the YAML builder produces a `Closed` that hits the same `validate`.
// A single definition here keeps the two paths from drifting (their other checks are type- vs. string-specific).
private[pipeline] object PlanValidation {

  // Walk the finished AST and apply every whole-plan invariant before execution.
  def validate(ast: Ast): Unit = ast match {
    case Ast.Src(_)       =>
    case Ast.Ref          =>
    case Ast.Fl(up, _)    => validate(up)
    case Ast.Mg(ups, _)   => ups.foreach { case (_, up) => validate(up) }
    case Ast.Sp(up, _)    => validate(up)
    case Ast.Port(sp, _)  => validate(sp)
    case Ast.Snk(up, _)   => validate(up)
    case Ast.Group(roots) => roots.foreach(validate)
    case Ast.Fork(up, branches) =>
      validate(up)
      branches.foreach(validateBranch)
  }

  // A fanOut branch consumes the shared upstream and terminates in a Sink; it cannot start a new Source.
  def validateBranch(ast: Ast): Unit = ast match {
    case Ast.Snk(up, _) => validateBranchBody(up)
    case other =>
      throw new IllegalArgumentException(s"fanOut branch must end in a Sink, got ${other.getClass.getSimpleName}")
  }

  private def validateBranchBody(ast: Ast): Unit = ast match {
    case Ast.Ref        =>
    case Ast.Fl(up, _)  => validateBranchBody(up)
    case Ast.Mg(ups, _) => ups.foreach { case (_, up) => validateBranchBody(up) }
    case Ast.Src(_) =>
      throw new IllegalArgumentException(
        "fanOut branch cannot introduce a new Source; consume the shared upstream only"
      )
    case other =>
      throw new IllegalArgumentException(s"fanOut branch cannot contain ${other.getClass.getSimpleName}")
  }
}
