package actionbase.pipeline.bulkload.step03

import actionbase.pipeline.bulkload.result.StepResult
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.tools.{DistCp, DistCpConstants, DistCpOptions}

/**
  * Core HFile DistCp algorithm.
  *
  * Given a source and destination URI, a path (file or directory), and a
  * Hadoop [[Configuration]], copy HFiles across clusters using Hadoop's
  * DistCp utility and return a [[StepResult]] describing the outcome.
  *
  * Cluster catalog, CLI parsing, and SparkSession / FileSystem driver
  * setup are intentionally left to the external consumer. The OSS module
  * only owns the algorithmic core (DistCp invocation + semantics around
  * [[Operation]]).
  *
  * @note Extracted in OSS port from AbstractCopyHFileBatch: algorithmic DistCp core only.
  */
object HFileDistCp extends StrictLogging {

  /**
    * DistCp semantics.
    *
    * - [[Operation.CREATE]] (default): fails when the destination already
    *   exists. Safe for the fresh-HFile workflow.
    * - [[Operation.UPSERT]]: synchronises an existing destination. Only use
    *   under operator supervision.
    */
  object Operation extends Enumeration {
    val UPSERT, CREATE = Value
  }

  case class Params(
      srcPath: Path,
      dstPath: Path,
      numOfMaps: Int = DistCpConstants.DEFAULT_MAPS,
      mapBandwidthMegaBytePerSecond: Int = DistCpConstants.DEFAULT_BANDWIDTH_MB,
      operation: Operation.Value = Operation.CREATE
  )

  def copy(conf: Configuration, params: Params, startAt: Long = System.currentTimeMillis()): StepResult = {
    val copiedPath                  = runDistCp(conf, params)
    val (countOfHFile, sizeOfHFile) = summarise(conf, params.srcPath)

    StepResult(
      batchClass = getClass,
      startAt = startAt,
      endAt = System.currentTimeMillis(),
      info = Map(
        "sourceHFilePath"  -> params.srcPath.toString,
        "copiedHFilePath"  -> copiedPath.toString,
        "numberOfHFile"    -> countOfHFile.toString,
        "sizeOfHFileBytes" -> sizeOfHFile.toString
      )
    )
  }

  private def runDistCp(conf: Configuration, params: Params): Path = {
    val srcFS = params.srcPath.getFileSystem(conf)
    if (!srcFS.exists(params.srcPath)) {
      throw new RuntimeException(s"Source path does not exist: ${params.srcPath}")
    }

    val sourcePathIsFile = srcFS.isFile(params.srcPath)
    val targetPath =
      if (sourcePathIsFile) {
        params.dstPath.getParent
      } else if (params.dstPath.getName == params.srcPath.getName) {
        params.dstPath.getParent
      } else {
        params.dstPath
      }

    val distCpOptions = new DistCpOptions(
      java.util.Arrays.asList(params.srcPath),
      targetPath
    )
    distCpOptions.setMaxMaps(params.numOfMaps)
    distCpOptions.setMapBandwidth(params.mapBandwidthMegaBytePerSecond)

    params.operation match {
      case Operation.UPSERT =>
        if (sourcePathIsFile) {
          distCpOptions.setOverwrite(true)
          distCpOptions.setSyncFolder(false)
          distCpOptions.setAppend(false)
          distCpOptions.setDeleteMissing(false)
        } else {
          distCpOptions.setSyncFolder(true)
          distCpOptions.setAppend(true)
          distCpOptions.setDeleteMissing(true)
        }

      case Operation.CREATE =>
        failIfExists(conf, params.dstPath)
    }

    distCpOptions.setIgnoreFailures(false)
    distCpOptions.preserve(DistCpOptions.FileAttribute.REPLICATION)
    distCpOptions.preserve(DistCpOptions.FileAttribute.PERMISSION)
    distCpOptions.preserve(DistCpOptions.FileAttribute.TIMES)

    val distCp = new DistCp(conf, distCpOptions)
    val job    = distCp.execute()
    logger.info(s"""DistCp job information
                   |  from        : ${params.srcPath}
                   |  to          : ${params.dstPath}
                   |  jobId       : ${job.getJobID}
                   |  jobName     : ${job.getJobName}
                   |  tracking    : ${job.getTrackingURL}
                   |  initialState: ${job.getJobState}
                   |""".stripMargin)

    val success = job.waitForCompletion(true)
    if (!success) throw new RuntimeException("DistCp job failed")

    failUnlessExists(conf, params.dstPath)
    params.dstPath
  }

  private def summarise(conf: Configuration, src: Path): (Long, Long) = {
    val fs      = src.getFileSystem(conf)
    val summary = fs.getContentSummary(src)
    (summary.getFileCount, summary.getLength)
  }

  private def failIfExists(conf: Configuration, path: Path): Unit = {
    val fs = path.getFileSystem(conf)
    if (fs.exists(path)) throw new RuntimeException(s"Destination path already exists: $path")
  }

  private def failUnlessExists(conf: Configuration, path: Path): Unit = {
    val fs = path.getFileSystem(conf)
    if (!fs.exists(path)) throw new RuntimeException(s"Destination path does not exist: $path")
  }
}
