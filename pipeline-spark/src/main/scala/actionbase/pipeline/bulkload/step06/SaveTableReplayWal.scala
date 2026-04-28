package actionbase.pipeline.bulkload.step06

import actionbase.core.AbInfo
import actionbase.pipeline.bulkload.result.StepResult
import actionbase.pipeline.bulkload.storage.AbEdgeStorage
import actionbase.pipeline.bulkload.wal.WalReader
import org.apache.spark.sql.SparkSession

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

/**
  * Step 6 algorithm — snapshot write-ahead-log entries that accumulated
  * against the source table while HFiles were being created.
  *
  * Window: `[from, newTableVersion + 1 min)`. The trailing one-minute
  * margin captures events emitted right up to the table version.
  *
  * @note Extracted in OSS port from SaveTableReplayWalBatch: algorithm without batch shell.
  */
object SaveTableReplayWal {

  case class Params(
      newTableAbInfo: AbInfo,
      from: ZonedDateTime,
      batchName: String
  )

  def run(
      walReader: WalReader,
      edgeStorage: AbEdgeStorage,
      params: Params,
      startAt: Long = System.currentTimeMillis()
  )(implicit spark: SparkSession): StepResult = {
    val until = parseTableVersion(params.newTableAbInfo.tableVersion).plusMinutes(1)

    val wal = walReader.read(
      clientAppName = params.batchName,
      abInfo = params.newTableAbInfo,
      from = params.from,
      until = until
    )

    val savedPath = edgeStorage.saveWalToLabel(params.newTableAbInfo, wal)

    StepResult(
      batchClass = getClass,
      startAt = startAt,
      endAt = System.currentTimeMillis(),
      info = Map(
        "tableName"   -> params.newTableAbInfo.fullLabelName,
        "replayFrom"  -> params.from.toString,
        "replayUntil" -> until.toString,
        "savedPath"   -> savedPath
      )
    )
  }

  private val KST: ZoneOffset = ZoneOffset.ofHours(9)
  private val TABLE_VERSION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  /**
    * Parse the 14-digit `yyyyMMdd_HHmmss` table version back into a KST
    * zoned time. Kept local so Step 6 does not share a helper module with
    * the params layer.
    */
  private def parseTableVersion(version: String): ZonedDateTime = {
    val digits = version.replace("_", "")
    LocalDateTime.parse(digits, TABLE_VERSION_FORMATTER).atZone(KST)
  }
}
