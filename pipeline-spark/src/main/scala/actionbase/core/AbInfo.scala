package actionbase.core

import actionbase.core.model.StorageType

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
  * Core identity of an ActionBase table: (serviceName, aliasName, storageType, namespace, phase, tableVersion).
  *
  * The in-house tenant catalog that used to live here has moved to the
  * external consumer. OSS callers construct [[AbInfo]] directly with
  * explicit arguments.
  */
case class AbInfo(
    serviceName: String,
    aliasName: String,
    storageType: StorageType,
    namespace: String,
    phase: String = AbInfo.currentPhase,
    tableVersion: String = ZonedDateTime.now(AbInfo.KST).format(AbInfo.TABLE_VERSION_FORMATTER)
) {
  val fullAliasName   = s"$serviceName.$aliasName"
  val prefix          = if (AbInfo.PROD_PHASE == phase) "" else phase
  val storageName     = s"${storageType.prefix}_${aliasName}_$tableVersion"
  val hBaseTableName  = if (AbInfo.PROD_PHASE == phase) s"$storageName" else s"${prefix}_$storageName"
  val labelNamePrefix = s"${aliasName}_"
  val labelName       = s"$labelNamePrefix$tableVersion"
  val fullLabelName   = s"$serviceName.$labelName"
  val tmpPath         = s"/tmp/actionbase_bulk_load/data/$hBaseTableName"
}

object AbInfo {

  /** Production phase identifier. Rows with a non-prod phase get a table-name prefix. */
  private[core] val PROD_PHASE = "real"

  /** KST offset used for default [[tableVersion]] rendering. */
  private[core] val KST: java.time.ZoneOffset = java.time.ZoneOffset.ofHours(9)

  private[core] val TABLE_VERSION_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

  /**
    * Current deployment phase, resolved from the `phase` HOCON key. Defaults to `"local"`
    * when no config is available so the OSS module stays self-contained.
    */
  private[core] def currentPhase: String =
    scala.util.Try(com.typesafe.config.ConfigFactory.load().getString("phase")).getOrElse("local")

  val DEFAULT_COLUMN_FAMILY: Array[Byte] = com.kakao.actionbase.v2.core.code.hbase.Constants.DEFAULT_COLUMN_FAMILY
  val DEFAULT_QUALIFIER: Array[Byte]     = com.kakao.actionbase.v2.core.code.hbase.Constants.DEFAULT_QUALIFIER
}
