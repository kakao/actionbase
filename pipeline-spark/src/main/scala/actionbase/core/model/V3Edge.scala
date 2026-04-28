package actionbase.core.model

/**
  * V3 edge payload (source + target + properties map) used by the V3 mutation
  * request. Properties are `Any` because the upstream `TraceEdge.getProps` is
  * `java.util.Map<String, Object>`.
  */
case class V3Edge(
    version: Long,
    source: Any,
    target: Any,
    properties: Map[String, Any]
)
