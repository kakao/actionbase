package actionbase.pipeline.bulkload.wal

import actionbase.core.AbInfo
import org.apache.spark.sql.{Dataset, SparkSession}

import java.time.ZonedDateTime

/**
  * Source of write-ahead-log records for a single ActionBase table.
  *
  * The production implementation reads from a Kafka WAL topic; tests and
  * local runs may provide an in-memory / filesystem backed version. The
  * concrete binding lives in the external consumer because it requires a
  * tenant-specific Kafka cluster, consumer group management and other
  * utilities which are not part of the OSS module.
  *
  * @note Reduced in OSS port: retains only the read() surface; Kafka binding lives in the external consumer.
  */
trait WalReader extends Serializable {

  /**
    * Read WAL records for `abInfo` in the half-open interval
    * `[from, until)`.
    *
    * @param clientAppName identifier used as the Kafka consumer group /
    *                      self-audit filter so re-runs on the same day do
    *                      not re-read their own output.
    * @param abInfo        table whose WAL should be read.
    * @param from          inclusive lower bound.
    * @param until         exclusive upper bound.
    */
  def read(
      clientAppName: String,
      abInfo: AbInfo,
      from: ZonedDateTime,
      until: ZonedDateTime
  )(implicit spark: SparkSession): Dataset[String]
}
