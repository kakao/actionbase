package actionbase.core.model

/**
  * Response shape of the V3 edge mutation endpoint. Restored algorithms do
  * not inspect the return value; this is kept as the trait signature target
  * only.
  */
case class V3EdgeMutationResponse(results: List[V3EdgeMutationResponseItem])

case class V3EdgeMutationResponseItem(
    source: String,
    target: String,
    status: String,
    count: Int
)
