package actionbase.core

import actionbase.core.AbService.GetLabelByAliasResponse
import actionbase.core.model.V3MultiEdgeMutationResponse.V3MultiEdgeMutationResponse
import actionbase.core.model.{
  V3EdgeCountQueryResponse,
  V3EdgeMutationRequest,
  V3EdgeMutationResponse,
  V3EdgeQueryResponse,
  V3MultiEdgeMutationRequest
}
import com.kakao.actionbase.v2.core.metadata.Direction

/**
  * V3 ActionBase service contract consumed by
  * [[actionbase.pipeline.bulkload.wal.WalReplayer]] (mutate endpoints) and
  * by external consumers that perform edge lookups. Concrete HTTP client
  * implementations live in the external consumer; OSS ships the trait
  * surface plus a fully in-memory fake for tests.
  */
trait V3AbService extends Serializable {

  def mutateEdge(
      actor: String,
      database: String,
      table: String,
      request: V3EdgeMutationRequest,
      syncForce: Boolean = false
  ): V3EdgeMutationResponse

  def mutateMultiEdge(
      actor: String,
      database: String,
      table: String,
      request: V3MultiEdgeMutationRequest
  ): V3MultiEdgeMutationResponse

  def getTableByAlias(database: String, alias: String): GetLabelByAliasResponse

  def getEdge(database: String, table: String, source: Any, target: Any): V3EdgeQueryResponse

  def scanEdges(
      database: String,
      table: String,
      index: String,
      start: Any,
      direction: Direction,
      ranges: Option[String] = None,
      filters: Option[String] = None,
      offset: Option[String] = None,
      limit: Int = 25
  ): V3EdgeQueryResponse

  def countEdges(database: String, table: String, start: Any, direction: Direction): V3EdgeCountQueryResponse
}
