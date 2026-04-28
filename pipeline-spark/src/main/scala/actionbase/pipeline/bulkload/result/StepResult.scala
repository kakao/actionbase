package actionbase.pipeline.bulkload.result

import com.typesafe.scalalogging.StrictLogging

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.TimeUnit

/**
  * Result of a single bulk-load step. Carries timing information plus a
  * free-form `info` map so concrete steps can surface step-specific metrics
  * (copied byte count, replay row count, ...).
  *
  * Kept minimal on purpose — the previous implementation depended on a
  * DateUtil/Human/JsonUtil helper family that was removed in iter-slim-8.
  * Formatting is now done via `java.time` and a private helper.
  */
case class StepResult(
    step: String,
    appName: String,
    info: Map[String, String],
    startAt: String,
    endAt: String,
    elapsed: String,
    startAtMs: Long,
    endAtMs: Long
) {
  def elapsedMs: Long = endAtMs - startAtMs

  def asMap: Map[String, String] =
    Map(
      s"$step.appName" -> appName,
      s"$step.startAt" -> startAt,
      s"$step.endAt"   -> endAt,
      s"$step.elapsed" -> elapsed
    ) ++ info.map { case (k, v) => s"$step.info.$k" -> v }

  def print(): Unit = {
    val lines = info.map { case (k, v) => s"|     - $k : $v" }.mkString("\n")
    StepResult.logInfo(s"""
                          |* StepResult
                          | - step      : $step
                          | - appName   : $appName
                          | - elapsed   : $elapsed
                          | - startAt   : $startAt
                          | - endAt     : $endAt
                          | - info
                          |$lines
                          |""".stripMargin)
  }
}

object StepResult extends StrictLogging {

  private[result] def logInfo(message: => String): Unit = logger.info(message)

  /** KST used for human-readable timestamps in the result map. */
  private val KST: ZoneOffset = ZoneOffset.ofHours(9)

  private val DISPLAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def apply(
      batchClass: Class[_],
      startAt: Long,
      endAt: Long,
      info: Map[String, String] = Map.empty
  ): StepResult = {
    val batchName = Option(batchClass.getSimpleName)
      .getOrElse(batchClass.getName)
      .stripSuffix("$")
    val lastPackage = Option(batchClass.getPackage)
      .map(_.getName)
      .getOrElse("")
      .split('.')
      .filter(_.nonEmpty)
      .lastOption
      .getOrElse("")
    val stepName =
      if (lastPackage.nonEmpty) lastPackage
      else s"step-unknown-${System.currentTimeMillis()}"
    apply(stepName, batchName, startAt, endAt, info)
  }

  def apply(
      step: String,
      appName: String,
      startAt: Long,
      endAt: Long,
      info: Map[String, String]
  ): StepResult = {
    val startStr = Instant.ofEpochMilli(startAt).atZone(KST).format(DISPLAY_FORMATTER)
    val endStr   = Instant.ofEpochMilli(endAt).atZone(KST).format(DISPLAY_FORMATTER)
    StepResult(
      step = step,
      appName = appName,
      info = info,
      startAt = startStr,
      endAt = endStr,
      elapsed = readableElapsed(endAt - startAt),
      startAtMs = startAt,
      endAtMs = endAt
    )
  }

  /**
    * Format a positive millisecond span as a short `Xd Yh Zm Ws` string.
    * Same behaviour as the removed `Human.readableTime` helper; only
    * non-zero segments are emitted.
    */
  private def readableElapsed(millis: Long): String = {
    if (millis < 0) return "0s"
    val days    = TimeUnit.MILLISECONDS.toDays(millis)
    val hours   = TimeUnit.MILLISECONDS.toHours(millis) - TimeUnit.DAYS.toHours(days)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis))
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
    val segments = Seq(
      (days, "d"),
      (hours, "h"),
      (minutes, "m"),
      (seconds, "s")
    ).dropWhile(_._1 == 0L).map { case (v, u) => s"$v$u" }
    if (segments.isEmpty) s"${millis}ms" else segments.mkString(" ")
  }
}
