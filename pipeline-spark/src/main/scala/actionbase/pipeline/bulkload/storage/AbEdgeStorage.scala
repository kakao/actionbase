package actionbase.pipeline.bulkload.storage

import actionbase.core.AbInfo
import org.apache.spark.sql.{Dataset, SparkSession}

/**
  * Persistence contract for bulk-load WAL datasets.
  *
  * The original implementation composed HDFS paths using an in-house
  * path catalog and wrote plain-text files to them. That catalog was
  * driver-specific (internal cluster names, internal root directories,
  * ...) and has been removed from OSS. The trait below captures only the
  * surface the Step 6-10 orchestrators actually need; concrete
  * implementations that know how to materialise the paths on a given
  * cluster live in the external consumer.
  *
  * @note Reduced in OSS port from the full AbEdgeStorage to the minimal read/write surface.
  */
trait AbEdgeStorage extends Serializable {

  /** Persist the table-replay WAL for `info` and return the written path. */
  def saveWalToLabel(info: AbInfo, ds: Dataset[String])(implicit spark: SparkSession): String

  /** Load the table-replay WAL previously written via [[saveWalToLabel]]. */
  def loadWalToLabel(info: AbInfo)(implicit spark: SparkSession): Dataset[String]

  /** Persist the alias-replay WAL for `info` and return the written path. */
  def saveWalToAlias(info: AbInfo, ds: Dataset[String])(implicit spark: SparkSession): String

  /** Load the alias-replay WAL previously written via [[saveWalToAlias]]. */
  def loadWalToAlias(info: AbInfo)(implicit spark: SparkSession): Dataset[String]
}
