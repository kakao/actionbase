package actionbase.core.model

import com.kakao.actionbase.v2.core.edge.{BulkLoadEdge, TraceEdge}

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

/**
  * V3 multi-edge payload. Carries an explicit `id` (extracted from the
  * `_id` property) in addition to the source/target pair.
  */
case class V3MultiEdge(
    version: Long,
    id: Any,
    source: Any,
    target: Any,
    properties: Map[String, Any]
)

object V3MultiEdge {
  private val ID_FIELD_ON_EVENT = "_id"

  def from(traceEdge: TraceEdge): V3MultiEdge =
    V3MultiEdge(
      version = traceEdge.getTs,
      id = traceEdge.getProps.get(ID_FIELD_ON_EVENT),
      source = traceEdge.getSrc,
      target = traceEdge.getTgt,
      properties = traceEdge.getProps.asScala.toMap
    )

  def of(id: String, bulkLoadEdge: BulkLoadEdge): V3MultiEdge =
    V3MultiEdge(
      version = bulkLoadEdge.getTs,
      id = id,
      source = bulkLoadEdge.getSrc,
      target = bulkLoadEdge.getTgt,
      properties = bulkLoadEdge.getProps.asScala.toMap
    )
}
