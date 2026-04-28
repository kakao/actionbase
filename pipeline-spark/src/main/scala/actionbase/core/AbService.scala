package actionbase.core

import actionbase.core.AbService.{EdgeResponse, GetLabelByAliasResponse, StorageCreate, StorageDTO}
import actionbase.core.model.{AbAudit, V2TableUpdateResponse}
import com.fasterxml.jackson.annotation.JsonProperty
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.{DirectionType, LabelDTO}
import io.circe.{Decoder, Encoder}

/**
  * V1 ActionBase service contract used by [[ActionBaseSyncV1]], the legacy
  * WAL replayer, and by external consumers that perform edge reads/writes
  * against the V1 endpoint surface. Concrete HTTP client implementations
  * live in the external consumer; OSS ships only the trait surface plus
  * companion DTOs.
  */
trait AbService extends Serializable {

  def createStorage(storageCreate: StorageCreate): StorageDTO
  def getStorage(storageName: String): StorageDTO
  def updateStorage(storage: StorageDTO): Unit
  def deleteStorage(storageName: String): Unit

  def createLabel(serviceName: String, labelName: String, label: LabelDTO): LabelDTO
  def updateLabelActive(serviceName: String, labelName: String, active: Boolean): V2TableUpdateResponse
  def updateAlias(serviceName: String, aliasName: String, fullLabelName: String): Unit
  def getLabel(serviceName: String, labelName: String): LabelDTO
  def getLabelByAlias(serviceName: String, aliasName: String): GetLabelByAliasResponse
  def deleteLabel(labelDTO: LabelDTO): Unit
  def listLabel(serviceName: String): Seq[LabelDTO]

  def findSelfEdges[T: Manifest](
      serviceName: String,
      labelName: String,
      src: Any
  ): EdgeResponse[T]

  def findEdges[T](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T]

  def findEdgesWithType[T: Encoder: Decoder](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T]

  def findPaginatedEdgesWithType[T: Encoder: Decoder](
      serviceName: String,
      labelName: String,
      src: Any,
      tgt: Option[Any] = None,
      fetchSize: Int = 10,
      direction: DirectionType = DirectionType.OUT,
      index: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[String] = None,
      select: Option[String] = None
  ): EdgeResponse[T]

  def insertEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit

  def updateEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit

  def deleteEdge(
      serviceName: String,
      labelName: String,
      edges: Seq[Edge],
      audit: Option[AbAudit] = None
  ): Unit

  def disableHbaseTable(tableFullName: String): Unit
  def deleteHbaseTable(tableFullName: String): Unit
}

object AbService {

  case class StorageCreate(
      name: String,
      desc: String,
      `type`: String,
      conf: StorageConf
  )

  case class StorageDTO(
      active: Boolean,
      name: String,
      desc: String,
      `type`: String,
      conf: StorageConf
  )

  case class StorageConf(
      namespace: String,
      tableName: String
  )

  case class GetLabelByAliasResponse(
      @JsonProperty("target") target: String,
      @JsonProperty("label") label: LabelDTO
  )

  /**
    * Jackson-based deserialisation cannot retain the concrete runtime
    * type of `T`; callers that need type-safety for the element type
    * should prefer the circe-based `findEdgesWithType` variant.
    */
  case class EdgeResponse[T](
      data: Seq[T],
      rows: Int,
      stats: Seq[String],
      offset: Option[String],
      hasNext: Boolean
  )
}
