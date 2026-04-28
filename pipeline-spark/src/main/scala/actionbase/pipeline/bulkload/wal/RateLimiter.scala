package actionbase.pipeline.bulkload.wal

/**
  * Minimal executor-side rate-limiter contract for the WAL replayer.
  * `untilReady` must block until the next permit is available.
  */
trait RateLimiter extends Serializable {
  def untilReady(): Unit
}
