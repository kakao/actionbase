package com.kakao.actionbase.pipeline.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

import scala.collection.JavaConverters._

/** Channel for a Job to emit named outputs the runner can pick up after the job completes. The runner sets
  * `actionbase.pipeline.outputFile` to a path before invoking `Job.main`; emit() appends `name=value` lines there.
  *
  * Mirrors GitHub Actions' `$GITHUB_OUTPUT` mechanism. When the system property is unset (e.g., the Job is invoked
  * directly by spark-submit outside a runner), emit() is a silent no-op.
  */
object JobOutputs {

  val SystemPropertyKey = "actionbase.pipeline.outputFile"

  def emit(name: String, value: String): Unit =
    Option(System.getProperty(SystemPropertyKey)).foreach { path =>
      val line = s"$name=$value\n"
      Files.write(
        Paths.get(path),
        line.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
      )
    }

  def read(path: String): Map[String, String] = {
    val file = Paths.get(path)
    if (!Files.exists(file)) Map.empty
    else
      Files
        .readAllLines(file, StandardCharsets.UTF_8)
        .asScala
        .iterator
        .flatMap { line =>
          line.split("=", 2) match {
            case Array(k, v) if k.nonEmpty => Some(k -> v)
            case _                         => None
          }
        }
        .toMap
  }
}
