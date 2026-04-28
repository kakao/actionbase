package actionbase.core.model

import com.kakao.actionbase.v2.core.metadata.EdgeOperation

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

case class V3EdgeMutation(
    `type`: EdgeOperation,
    edge: V3Edge
)

object V3EdgeMutation {

  /**
    * Convert a parsed [[AbWal]] into a V3 mutation payload. Called inside
    * `foreachPartition` after sorting, so it stays a small pure function.
    */
  def of(wal: AbWal): V3EdgeMutation = {
    val edge = wal.edge
    V3EdgeMutation(
      `type` = wal.op,
      edge = V3Edge(
        version = edge.getTs,
        source = edge.getSrc.toString,
        target = edge.getTgt.toString,
        properties = edge.getProps.asScala.toMap
      )
    )
  }
}
