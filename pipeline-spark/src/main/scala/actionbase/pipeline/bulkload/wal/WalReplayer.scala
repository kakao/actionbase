package actionbase.pipeline.bulkload.wal

import actionbase.core.V3AbService
import actionbase.core.model.{
  AbWal,
  V3EdgeMutation,
  V3EdgeMutationRequest,
  V3MultiEdgeMutation,
  V3MultiEdgeMutationRequest
}
import actionbase.pipeline.support.Retry.withRetry
import com.kakao.actionbase.v2.core.metadata.LabelType
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.concurrent.duration.DurationInt

/**
  * V3 ActionBase WAL replayer. Reads raw JSON WAL records from a
  * `Dataset[String]`, partitions by (source, target), sorts within each
  * bulk batch by (label, source, target, timestamp), then dispatches to
  * the V3 mutate endpoint. Rate-limiting is applied per batch *before*
  * retries; retry is fixed at 3 attempts with 3s spacing.
  */
class WalReplayer(
    rateLimiter: () => RateLimiter,
    abService: () => V3AbService,
    private val numPartitions: Int,
    private val bulkSize: Int
) extends Serializable {

  @transient private lazy val service: V3AbService    = abService()
  @transient private lazy val limiter: RateLimiter    = rateLimiter()

  def replay(
      auditor: String,
      database: String,
      tableAlias: String,
      replayTargetTableName: String,
      ds: Dataset[String]
  )(implicit spark: SparkSession): Unit = {
    val tableType = service.getTableByAlias(database = database, alias = tableAlias).label.getType
    if (tableType == LabelType.MULTI_EDGE) {
      replayWalOfMultiEdge(database = database, table = replayTargetTableName, actor = auditor, ds = ds)
    } else {
      replayWalOfEdge(database = database, table = replayTargetTableName, actor = auditor, ds = ds)
    }
  }

  /**
    * Group as many records as possible into a single bulk call:
    *   1. co-locate by (source, target)
    *   2. sort by (label, source, target, timestamp)
    *   3. flush in `bulkSize` chunks
    *
    * Keep `Dataset[String]` to dodge the Encoder issue; parse to `AbWal`
    * inside `foreachPartition`.
    */
  private def replayWalOfEdge(database: String, table: String, actor: String, ds: Dataset[String])(
      implicit spark: SparkSession
  ): Unit = {
    import spark.implicits._
    ds.flatMap { rawWal =>
        AbWal.parse(rawWal).map { wal =>
          val partitionKey = (wal.edge.getSrc, wal.edge.getTgt).hashCode % numPartitions
          (partitionKey, rawWal)
        }
      }
      .repartition(numPartitions, $"_1")
      .foreachPartition { xs: Iterator[(_, String)] =>
        xs.grouped(bulkSize).foreach { seq =>
          val chunk = seq
            .flatMap { case (_, rawWal) => AbWal.parse(rawWal) }
            .sortBy(wal => (wal.label, wal.edge.getSrc.toString, wal.edge.getTgt.toString, wal.edge.getTs))
            .map(V3EdgeMutation.of)

          if (chunk.nonEmpty) {
            limiter.untilReady()

            withRetry(3, 3.seconds) {
              // Force synchronous mode regardless of table mode.
              val request = V3EdgeMutationRequest(mutations = chunk)
              service.mutateEdge(actor, database, table, request = request, syncForce = true)
            }
          }
        }
      }
  }

  private def replayWalOfMultiEdge(database: String, table: String, actor: String, ds: Dataset[String])(
      implicit spark: SparkSession
  ): Unit = {
    import spark.implicits._
    ds.flatMap { rawWal =>
        AbWal.parse(rawWal).map { wal =>
          val partitionKey = wal.edge.getSrc.hashCode % numPartitions
          (partitionKey, rawWal)
        }
      }
      .repartition(numPartitions, $"_1")
      .foreachPartition { xs: Iterator[(_, String)] =>
        xs.grouped(bulkSize)
          .foreach { seq =>
            val events = seq
              .flatMap { case (_, rawWal) => AbWal.parse(rawWal) }
              .map(V3MultiEdgeMutation.of)

            if (events.nonEmpty) {
              limiter.untilReady()

              withRetry(3, 3.seconds) {
                service
                  .mutateMultiEdge(actor, database, table, request = V3MultiEdgeMutationRequest(mutations = events))
              }
            }
          }
      }
  }
}
