package actionbase.core.model

import com.kakao.actionbase.v2.core.edge.Edge

/**
  * Result envelope returned by the V1 edge mutation endpoints. The restored
  * [[actionbase.core.AbServiceImpl]] inspects `result` for an element with
  * `status == "ERROR"` to decide whether to raise
  * [[actionbase.core.exception.MutationResultErrorException]].
  */
object AbResponse {

  case class MutationResult(
      result: Seq[MutationResultItem]
  )

  case class MutationResultItem(
      status: String,
      traceId: String,
      edge: Option[Edge]
  )
}
