package com.kakao.actionbase.pipeline.workflow

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import java.io.File

/** Loads a GHA-native workflow YAML and adapts it to the internal `Workflow` / `JobSpec` model.
  *
  * The user's YAML is a real GitHub Actions workflow. Each job has one step that uses the actionbase pipeline runner
  * action — that step's `with:` carries the entry-point class, Cfg, and submit flags. The loader extracts those and
  * synthesizes the internal `JobSpec`. Multi-step GHA jobs (e.g., `checkout` + `pipeline-runner`) are tolerated; only
  * the pipeline-runner step is consumed.
  *
  * Also auto-loads every `.yaml` file under `<workflowDir>/preset/` into `Workflow.presets`.
  */
object WorkflowLoader {

  /** Marker substring used to find the actionbase pipeline-runner step among a job's steps. Matches both
    * `kakao/actionbase/actions/pipeline-runner@<ref>` (monorepo subpath) and a future standalone repo.
    */
  private val PipelineRunnerActionMarker = "actionbase/actions/pipeline-runner"

  @transient private lazy val yaml: YAMLMapper with ClassTagExtensions = {
    val m = new YAMLMapper() with ClassTagExtensions
    m.registerModule(DefaultScalaModule)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m
  }

  def load(path: String): Workflow = load(new File(path))

  def load(file: File): Workflow = {
    val gha         = yaml.readValue(file, classOf[GhaWorkflow])
    val internal    = adapt(gha)
    val filePresets = loadPresetDir(file.getParentFile)
    internal.copy(presets = internal.presets ++ filePresets)
  }

  private def adapt(gha: GhaWorkflow): Workflow = {
    val schedule = extractSchedule(gha.on)
    val jobs = gha.jobs.map { case (id, ghaJob) =>
      id -> adaptJob(id, ghaJob)
    }
    Workflow(
      name = gha.name,
      schedule = schedule,
      env = gha.env,
      presets = gha.presets,
      jobs = jobs
    )
  }

  /** Pull a cron string out of `on:` if present. GHA accepts string / array / map shapes; only the map form can hold
    * `schedule:`. Walks both Scala and Java collection types since Jackson's `Any` binding can produce either.
    */
  private def extractSchedule(on: Option[Any]): Option[String] = on.flatMap {
    case m: scala.collection.Map[_, _] =>
      asScheduleEntries(m.asInstanceOf[scala.collection.Map[String, Any]].get("schedule"))
    case jm: java.util.Map[_, _] =>
      val k = jm.asInstanceOf[java.util.Map[String, Any]]
      asScheduleEntries(Option(k.get("schedule")))
    case _ => None
  }

  private def asScheduleEntries(v: Option[Any]): Option[String] = v.flatMap {
    case s: scala.collection.Seq[_] =>
      s.headOption.flatMap(cronOf)
    case jl: java.util.List[_] =>
      val it = jl.iterator()
      if (it.hasNext) cronOf(it.next()) else None
    case _ => None
  }

  private def cronOf(item: Any): Option[String] = item match {
    case m: scala.collection.Map[_, _] =>
      m.asInstanceOf[scala.collection.Map[String, Any]].get("cron").map(_.toString)
    case jm: java.util.Map[_, _] =>
      Option(jm.asInstanceOf[java.util.Map[String, Any]].get("cron")).map(_.toString)
    case _ => None
  }

  private def adaptJob(id: String, gha: GhaJob): JobSpec = {
    val runner = gha.steps
      .find(_.uses.exists(_.contains(PipelineRunnerActionMarker)))
      .getOrElse(
        throw new IllegalArgumentException(
          s"job '$id' must include a step that `uses: kakao/actionbase/actions/pipeline-runner@...`"
        )
      )

    val w         = runner.`with`
    val className = w.get("class") match {
      case Some(s: String) => s
      case _               =>
        throw new IllegalArgumentException(s"job '$id' pipeline-runner step requires `with.class:`")
    }
    val config = asMap(w.get("config")).getOrElse(Map.empty[String, Any])
    val submit = asMap(w.get("submit")).getOrElse(Map.empty[String, Any])

    JobSpec(
      uses = s"actionbase/pipeline@0.x:$className",
      needs = gha.needs,
      `if` = gha.`if`,
      `with` = config,
      submit = submit
    )
  }

  private def asMap(v: Option[Any]): Option[Map[String, Any]] = v match {
    case Some(m: Map[_, _]) => Some(m.asInstanceOf[Map[String, Any]])
    case Some(s: String) if s.trim.nonEmpty =>
      // `with:` inputs are strings in real GHA; the action would parse them as YAML at runtime.
      // We do the same so a YAML block-scalar form (`config: |\n  ...`) also works.
      Some(yaml.readValue(s, classOf[Map[String, Any]]))
    case _ => None
  }

  private def loadPresetDir(workflowDir: File): Map[String, Map[String, Any]] = {
    if (workflowDir == null) return Map.empty
    val dir = new File(workflowDir, "preset")
    if (!dir.isDirectory) return Map.empty

    val files = Option(dir.listFiles).getOrElse(Array.empty[File])
    files.iterator
      .filter(f => f.isFile && f.getName.endsWith(".yaml"))
      .map { f =>
        val name = f.getName.stripSuffix(".yaml")
        val map  = yaml.readValue(f, classOf[Map[String, Any]])
        name -> map
      }
      .toMap
  }
}
