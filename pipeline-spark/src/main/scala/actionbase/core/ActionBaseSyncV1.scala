package actionbase.core

import actionbase.core.AbService.{StorageConf, StorageCreate}
import actionbase.core.model.V2TableUpdateResponse
import com.kakao.actionbase.v2.core.metadata.{LabelDTO => AbTable}
import com.typesafe.scalalogging.StrictLogging

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.DurationInt

/**
  * Orchestrates V1 ActionBase metadata synchronisation for a newly bulk-loaded
  * table: storage creation, label creation, alias swap, and old-table
  * deactivation.
  *
  * Each "wait for sync" phase polls the [[AbAdminService]] at 10 second
  * intervals with a 3 minute overall limit. The external consumer provides
  * the concrete [[AbService]] + [[AbAdminService]] HTTP client impls.
  *
  * @note Slimmed in OSS port: APIHelper and LazyEval removed; collaborators passed as thunks.
  */
class ActionBaseSyncV1(
    abAdminService: () => AbAdminService,
    abService: () => AbService
) extends StrictLogging {

  private val limit = 3.minutes.toMillis

  @transient private lazy val admin: AbAdminService = abAdminService()
  @transient private lazy val service: AbService    = abService()

  /**
    * Create both the storage and the label for the new table version.
    *
    * @return `(storageSyncElapsed, tableSyncElapsed)` as human-readable
    *         duration strings.
    */
  def createMetadata(
      abInfo: AbInfo,
      originalStorageName: String,
      newTable: AbTable
  ): (String, String) = {
    val storageSyncElapsed = createActionBaseStorage(abInfo, originalStorageName)
    val tableSyncElapsed   = createActionBaseTable(abInfo, newTable)

    storageSyncElapsed -> tableSyncElapsed
  }

  private def createActionBaseStorage(abInfo: AbInfo, originalStorageName: String): String = {
    val storageConf = StorageConf(
      namespace = abInfo.namespace,
      tableName = abInfo.hBaseTableName
    )

    val originalStorage = service.getStorage(originalStorageName)

    logger.info("Started to create ActionBase Storage")

    val elapsedMs = elapsedMillis {
      val startTime = System.currentTimeMillis()

      service.createStorage(
        storageCreate =
          StorageCreate(name = abInfo.storageName, desc = originalStorage.desc, `type` = "HBASE", conf = storageConf)
      )

      while (!admin.isStorageSynced(storageName = abInfo.storageName)
             && System.currentTimeMillis() - startTime < limit) {
        Thread.sleep(10.seconds.toMillis)

        logger.debug("The Storage has not yet been synced")
      }

      if (!admin.isStorageSynced(storageName = abInfo.storageName)) {
        throw new TimeoutException(
          s"Storage ${abInfo.storageName} failed to sync within ${limit}ms"
        )
      }
    }

    val elapsed = readableTime(elapsedMs)
    logger.info(s">> The Storage is in sync. it takes ($elapsed)")
    elapsed
  }

  /**
    * Create the V1 label corresponding to `newTable`.
    *
    * Name layout reference:
    *
    * - `[AB] serviceName`              example
    * - `[AB] storageName`              `{StorageType.prefix}_${alias}_${tableVersion}` — e.g. `st1_example_label_v1_20250813_103900`
    * - `[HBase] tableName`             same as storageName
    * - `tableName(labelName)`          `${alias}_${tableVersion}`                    — e.g. `example_label_v1_20250813_103900`
    * - `fullTableName(fullLabelName)`  `${serviceName}.${labelName}`                  — e.g. `example.example_label_v1_20250813_103900`
    */
  private def createActionBaseTable(abInfo: AbInfo, newTable: AbTable): String = {
    logger.info("Started to create ActionBase Table")

    val elapsedMs = elapsedMillis {
      val startTime = System.currentTimeMillis()

      service.createLabel(serviceName = abInfo.serviceName, labelName = abInfo.labelName, label = newTable)

      while (!admin.isLabelSynced(service = abInfo.serviceName, labelName = abInfo.labelName)
             && System.currentTimeMillis() - startTime < limit) {
        Thread.sleep(10.seconds.toMillis)

        logger.debug("The Table has not yet been synced")
      }

      if (!admin.isLabelSynced(service = abInfo.serviceName, labelName = abInfo.labelName)) {
        throw new TimeoutException(
          s"Label ${abInfo.serviceName}.${abInfo.labelName} failed to sync within ${limit}ms"
        )
      }
    }

    val elapsed = readableTime(elapsedMs)
    logger.info(s">> The Table is in sync. it takes ($elapsed)")
    elapsed
  }

  def changeTableAlias(abInfo: AbInfo): Unit = {
    logger.info(s"Started to update Alias to new table '${abInfo.fullLabelName}'")

    val elapsedMs = elapsedMillis {
      val startTime = System.currentTimeMillis()

      service.updateAlias(
        serviceName = abInfo.serviceName,
        aliasName = abInfo.aliasName,
        fullLabelName = abInfo.fullLabelName
      )

      while (!admin.isAliasSynced(abInfo.serviceName, abInfo.aliasName) &&
             System.currentTimeMillis() - startTime < limit) {
        Thread.sleep(10.seconds.toMillis)
        logger.debug("The Alias has not yet been synced")
      }

      if (!admin.isAliasSynced(abInfo.serviceName, abInfo.aliasName)) {
        throw new TimeoutException(
          s"Alias ${abInfo.serviceName}.${abInfo.aliasName} failed to sync within ${limit}ms"
        )
      }
    }

    val elapsed = readableTime(elapsedMs)
    logger.info(s">> The alias is synced with table '${abInfo.fullLabelName}'($elapsed)")
  }

  def deactivateOldTable(databaseName: String, originalTable: AbTable): Unit = {
    logger.info(s"Started to deactivate old table '${originalTable.getName}'")
    val response: V2TableUpdateResponse =
      service.updateLabelActive(serviceName = databaseName, labelName = originalTable.getName, active = false)

    if (response.isUpdated) {
      logger.info(s">> The table '${originalTable.getName}' is deactivated")
    } else {
      logger.warn(s">> The table '${originalTable.getName}' is not deactivated. response: $response")
    }
  }

  private def elapsedMillis[R](block: => R): Long = {
    val start = System.nanoTime()
    block
    (System.nanoTime() - start) / (1000L * 1000L)
  }

  private def readableTime(millis: Long): String = {
    if (millis < 0) return "0ms"
    val totalHours   = TimeUnit.MILLISECONDS.toHours(millis)
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val days         = TimeUnit.MILLISECONDS.toDays(millis)
    val hours        = totalHours - TimeUnit.DAYS.toHours(days)
    val minutes      = totalMinutes - TimeUnit.HOURS.toMinutes(totalHours)
    val seconds      = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(totalMinutes)
    val segments = Seq(
      (days, "d"),
      (hours, "h"),
      (minutes, "m"),
      (seconds, "s")
    ).dropWhile(_._1 == 0L).map { case (v, u) => s"$v$u" }
    if (segments.isEmpty) s"${millis}ms" else segments.mkString(" ")
  }
}
