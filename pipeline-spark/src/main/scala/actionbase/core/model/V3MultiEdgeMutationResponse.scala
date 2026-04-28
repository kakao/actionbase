package actionbase.core.model

/**
  * Response shape of the V3 multi-edge mutation endpoint. Companion object
  * kept for import parity with the driver even though the restored
  * algorithm does not inspect the body.
  */
object V3MultiEdgeMutationResponse {
  case class V3MultiEdgeMutationResponse(
      results: Seq[Item]
  ) {
    def hasError: Boolean = results.exists(_.status == "ERROR")
  }

  case class Item(
      id: String,
      status: String,
      count: Int
  )
}
