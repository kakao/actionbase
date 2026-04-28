package actionbase.pipeline.bulkload.wal

import actionbase.core.AbService
import actionbase.core.model.{AbAudit, AbWal}
import actionbase.pipeline.support.Retry.withRetry
import com.kakao.actionbase.v2.core.metadata.{EdgeOperation, LabelType}
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.concurrent.duration.DurationInt

/**
  * V2 (legacy) ActionBase WAL replayer. Groups WAL records per edge
  * operation (INSERT / UPDATE / DELETE) within each bulk batch and
  * dispatches via the [[AbService]] trait. Partitioning matches the V3
  * path so edges with the same (source, target) land on the same executor.
  */
class WalReplayerLegacy(
    abService: () => AbService,
    rateLimiter: () => RateLimiter,
    private val numPartitions: Int,
    private val bulkSize: Int
) extends Serializable {

  @transient private lazy val service: AbService   = abService()
  @transient private lazy val limiter: RateLimiter = rateLimiter()

  def replay(
      auditor: String,
      database: String,
      tableAlias: String,
      replayTargetTableName: String,
      ds: Dataset[String]
  )(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val tableType = service.getLabelByAlias(serviceName = database, aliasName = tableAlias).label.getType
    require(tableType != LabelType.MULTI_EDGE, "MULTI_EDGE is not supported in V2 Actionbase")

    val actor = Some(AbAudit(actor = auditor))

    // Keep Dataset[String] to dodge the Encoder issue; parse to AbWal in foreachPartition.
    ds.flatMap { rawWal =>
        AbWal.parse(rawWal).map { wal =>
          val partitionKey = (wal.edge.getSrc, wal.edge.getTgt).hashCode % numPartitions
          (partitionKey, rawWal)
        }
      }
      .repartition(numPartitions, $"_1")
      .foreachPartition { xs: Iterator[(_, String)] =>
        xs.map(_._2).grouped(bulkSize).foreach { seq =>
          val logsPerOp = seq.flatMap(AbWal.parse).groupBy(_.op)

          val insertEdges = logsPerOp.getOrElse(EdgeOperation.INSERT, Seq.empty).map(_.edge)
          val updateEdges = logsPerOp.getOrElse(EdgeOperation.UPDATE, Seq.empty).map(_.edge)
          val deleteEdges = logsPerOp.getOrElse(EdgeOperation.DELETE, Seq.empty).map(_.edge)

          if (insertEdges.nonEmpty) {
            withRetry(3, 3.seconds) {
              limiter.untilReady()
              service.insertEdge(database, replayTargetTableName, insertEdges, actor)
            }
          }
          if (updateEdges.nonEmpty) {
            withRetry(3, 3.seconds) {
              limiter.untilReady()
              service.updateEdge(database, replayTargetTableName, updateEdges, actor)
            }
          }
          if (deleteEdges.nonEmpty) {
            withRetry(3, 3.seconds) {
              limiter.untilReady()
              service.deleteEdge(database, replayTargetTableName, deleteEdges, actor)
            }
          }
        }
      }
  }
}
