package actionbase.core.model

case class V3EdgeQueryResponse(
    edges: Seq[V3Edge],
    count: Int,
    total: Int,
    offset: Option[String],
    hasNext: Boolean
)
