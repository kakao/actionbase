package com.kakao.actionbase.pipeline.runner

/** Resolves a Job or Step class name from a workflow YAML. The name may be:
  *   - a full FQN (`com.kakao.actionbase.pipeline.jobs.SparkPiJob`),
  *   - a short name (`SparkPiJob`), resolved relative to one of `roots`,
  *   - or a sub-package name (`subdir.MySource`), also resolved relative to a root.
  *
  * Roots are tried in order; first match wins.
  */
object ClassResolver {

  val JobRoots: Seq[String] = Seq("com.kakao.actionbase.pipeline.jobs")

  val StepRoots: Seq[String] = Seq(
    "com.kakao.actionbase.pipeline.steps.source",
    "com.kakao.actionbase.pipeline.steps.transform",
    "com.kakao.actionbase.pipeline.steps.sink"
  )

  def resolve(name: String, roots: Seq[String]): Class[_] = {
    val candidates = name +: roots.map(r => s"$r.$name")
    candidates.view.flatMap(tryLoad).headOption.getOrElse {
      throw new ClassNotFoundException(
        s"Cannot resolve '$name' as a full FQN or under any of: ${roots.mkString(", ")}"
      )
    }
  }

  private def tryLoad(fqn: String): Option[Class[_]] =
    try Some(Class.forName(fqn))
    catch { case _: ClassNotFoundException => None }
}
