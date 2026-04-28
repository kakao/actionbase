package actionbase.pipeline.bulkload.step04

import actionbase.core.AbInfo
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.tool.BulkLoadHFiles

import scala.concurrent.duration.DurationInt

class HBaseBulkLoader extends Serializable with StrictLogging {

  def execute(newTableAbInfo: AbInfo, hdfsHFileUriPath: String, hbaseConfiguration: Configuration): Unit = {
    validate(hdfsHFileUriPath, hbaseConfiguration)

    hbaseConfiguration.set("hbase.rpc.timeout", 30.minutes.toMillis.toString)
    hbaseConfiguration.set("hbase.client.operation.timeout", 30.minutes.toMillis.toString)

    val tableName = TableName.valueOf(newTableAbInfo.namespace, newTableAbInfo.hBaseTableName)
    val result = BulkLoadHFiles
      .create(hbaseConfiguration)
      .bulkLoad(tableName, new Path(hdfsHFileUriPath))

    validateResult(result, hdfsHFileUriPath)
  }

  private[step04] def validate(hdfsHFileUriPath: String, configuration: Configuration): Unit = {
    val hdfsPath = new Path(hdfsHFileUriPath)
    val fs       = hdfsPath.getFileSystem(configuration)

    if (!fs.exists(hdfsPath)) {
      throw new IllegalArgumentException(s"HFile path does not exist: $hdfsHFileUriPath")
    }

    val subDirs = fs.listStatus(hdfsPath).filter(_.isDirectory)
    if (subDirs.isEmpty) {
      throw new IllegalStateException(
        s"No column family subdirectories found in: $hdfsHFileUriPath"
      )
    }

    val hasHFiles = subDirs.exists { dir =>
      fs.listStatus(dir.getPath).exists(_.isFile)
    }
    if (!hasHFiles) {
      throw new IllegalStateException(
        s"No HFile found in column family subdirectories under: $hdfsHFileUriPath"
      )
    }
  }

  private def validateResult(
      result: java.util.Map[BulkLoadHFiles.LoadQueueItem, java.nio.ByteBuffer],
      hdfsHFileUriPath: String
  ): Unit = {
    if (result == null || result.isEmpty) {
      throw new IllegalStateException(
        s"Bulk load completed but no files were loaded from: $hdfsHFileUriPath"
      )
    }
    logger.info(s"[HBaseBulkLoader] Successfully loaded ${result.size()} HFile(s) from: $hdfsHFileUriPath")
  }
}
