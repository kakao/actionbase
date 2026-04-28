package actionbase.pipeline.bulkload.step09

import actionbase.core.AbInfo
import actionbase.pipeline.bulkload.result.StepResult
import actionbase.pipeline.bulkload.storage.AbEdgeStorage
import actionbase.pipeline.bulkload.wal.WalReader
import org.apache.spark.sql.SparkSession

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

/**
  * Step 9 algorithm — snapshot WAL entries written after the alias swap
  * so Step 10 can replay them against the new alias.
  *
  * Window: `[newTableVersion - 1 min, now + 1 min)`. The one-minute
  * overlap on both ends is intentional: replay is idempotent and this
  * guarantees no mutations emitted at the alias-swap boundary are lost.
  *
  * @note Extracted in OSS port from SaveAliasReplayWalBatch: algorithm without batch shell.
  */
object SaveAliasReplayWal {

  case class Params(
      newTableAbInfo: AbInfo,
      batchName: String
  )

  def run(
      walReader: WalReader,
      edgeStorage: AbEdgeStorage,
      params: Params,
      startAt: Long = System.currentTimeMillis(),
      now: () => ZonedDateTime = () => ZonedDateTime.now(KST)
  )(implicit spark: SparkSession): StepResult = {
    val from  = parseTableVersion(params.newTableAbInfo.tableVersion).minusMinutes(1)
    val until = now().plusMinutes(1)

    val wal = walReader.read(
      clientAppName = params.batchName,
      abInfo = params.newTableAbInfo,
      from = from,
      until = until
    )

    val savedPath = edgeStorage.saveWalToAlias(params.newTableAbInfo, wal)

    StepResult(
      batchClass = getClass,
      startAt = startAt,
      endAt = System.currentTimeMillis(),
      info = Map(
        "tableName"   -> params.newTableAbInfo.fullLabelName,
        "replayFrom"  -> from.toString,
        "replayUntil" -> until.toString,
        "savedPath"   -> savedPath
      )
    )
  }

  private val KST: ZoneOffset = ZoneOffset.ofHours(9)
  private val TABLE_VERSION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  private def parseTableVersion(version: String): ZonedDateTime = {
    val digits = version.replace("_", "")
    LocalDateTime.parse(digits, TABLE_VERSION_FORMATTER).atZone(KST)
  }
}
