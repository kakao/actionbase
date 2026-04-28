package actionbase.pipeline.bulkload.wal

/** Target ActionBase API version for the WAL replayer (V2 vs V3 dispatch). */
sealed trait ApiVersion

object ApiVersion {
  case object V2 extends ApiVersion
  case object V3 extends ApiVersion
}
