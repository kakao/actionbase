package actionbase.core.model

import com.kakao.actionbase.v2.core.metadata.EdgeOperation

case class V3MultiEdgeMutation(
    `type`: EdgeOperation = EdgeOperation.INSERT,
    edge: V3MultiEdge
)

object V3MultiEdgeMutation {
  def of(wal: AbWal): V3MultiEdgeMutation =
    V3MultiEdgeMutation(`type` = wal.op, edge = V3MultiEdge.from(traceEdge = wal.edge))
}
