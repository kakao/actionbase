package actionbase.pipeline.support

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.Duration

/**
  * Lightweight retry helper mirroring the common `withRetry(attempts, pause)(body)`
  * pattern. Invocations run `body` up to `times + 1` total attempts; on the
  * final attempt the originating exception propagates.
  *
  * Extracted as a neutral `object` so WAL-replayer algorithms can call it
  * without pulling in any external helper hierarchy.
  */
object Retry extends StrictLogging {

  def withRetry[A](times: Int, pause: Duration)(body: => A): A = {
    var result: Option[A] = None
    var remaining         = times
    while (remaining >= 0) {
      try {
        result = Some(body)
        remaining = -1
      } catch {
        case t: Throwable if remaining > 0 =>
          logger.warn(
            s"""=== with retry ===
               |* retry count : $times
               |* remain count : $remaining
               |* pause : $pause
               |* exception message
               |${t.getMessage}""".stripMargin,
            t
          )
          remaining -= 1
          Thread.sleep(pause.toMillis)
      }
    }
    result.get
  }
}
