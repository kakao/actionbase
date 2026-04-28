package actionbase.pipeline.bulkload.wal

import actionbase.core.AbInfo
import actionbase.pipeline.bulkload.storage.AbEdgeStorage
import org.apache.spark.sql.{Dataset, SparkSession}

/**
  * Replay a persisted WAL snapshot back into ActionBase — either into a newly
  * built table ([[replayTable]]) or against the current alias ([[replayAlias]]).
  * Dispatches to V2 [[WalReplayerLegacy]] or V3 [[WalReplayer]] based on
  * [[apiVersion]]; both replayers are injected as thunks so the executor
  * instantiates them once per Spark task.
  */
object WalReplayJob {

  /** Auditor name used in mutation audit fields when replaying WAL entries. */
  val auditorName: String = "BulkLoadWalReplayJob"
}

class WalReplayJob(
    apiVersion: ApiVersion,
    walReplayer: () => WalReplayer,
    walReplayerV2: () => WalReplayerLegacy
) extends Serializable {

  @transient private lazy val v3 = walReplayer()
  @transient private lazy val v2 = walReplayerV2()

  def replayAlias(newTableAbInfo: AbInfo, edgeStorage: AbEdgeStorage)(implicit spark: SparkSession): Unit = {
    val rawDS = edgeStorage.loadWalToAlias(newTableAbInfo)
    run(
      rawDS = rawDS,
      replayDatabase = newTableAbInfo.serviceName,
      replayTable = newTableAbInfo.aliasName,
      tableAlias = newTableAbInfo.aliasName
    )
  }

  def replayTable(newTableAbInfo: AbInfo, edgeStorage: AbEdgeStorage)(implicit spark: SparkSession): Unit = {
    val rawDS = edgeStorage.loadWalToLabel(newTableAbInfo)
    run(
      rawDS = rawDS,
      replayDatabase = newTableAbInfo.serviceName,
      replayTable = newTableAbInfo.labelName,
      tableAlias = newTableAbInfo.aliasName
    )
  }

  private def run(
      rawDS: Dataset[String],
      replayDatabase: String,
      replayTable: String,
      tableAlias: String
  )(implicit spark: SparkSession): Unit = {
    apiVersion match {
      case ApiVersion.V2 =>
        v2.replay(
          auditor = WalReplayJob.auditorName,
          database = replayDatabase,
          replayTargetTableName = replayTable,
          tableAlias = tableAlias,
          ds = rawDS
        )
      case ApiVersion.V3 =>
        v3.replay(
          auditor = WalReplayJob.auditorName,
          database = replayDatabase,
          replayTargetTableName = replayTable,
          tableAlias = tableAlias,
          ds = rawDS
        )
    }
  }
}
