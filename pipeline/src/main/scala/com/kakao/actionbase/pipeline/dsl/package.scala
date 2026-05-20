package com.kakao.actionbase.pipeline

import scala.language.implicitConversions

package object dsl {

  // The implicit edge label for an unlabeled output: the linear-chain default and a Merge's single-input view name.
  private[pipeline] val DefaultLabel: String = "_0"

  // Lets a Source start a `~>` chain (the operator is defined on Plan.Open).
  implicit def sourceToOpen(s: Source): Plan.Open =
    new Plan.Open(Ast.Src(s))
}
