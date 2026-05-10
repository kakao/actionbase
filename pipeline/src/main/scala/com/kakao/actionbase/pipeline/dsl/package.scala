package com.kakao.actionbase.pipeline

import scala.language.implicitConversions

package object dsl {
  // Lets a Source start a chain: `MySource(...) ~> MyTransform() ~> MySink(...)`.
  // `~>` lives on Plan.Open, so this conversion bridges the gap.
  implicit def sourceToOpen(s: Source): Plan.Open =
    new Plan.Open(Ast.Src(s))
}
