package com.kakao.actionbase.core.edge.payload

data class AggregationsTopkResponse(
    val topks: List<TopkItem>,
    val count: Int,
) {
    data class TopkItem(
        val value: String,
        val metric: Long,
        val properties: Map<String, String>,
    )

    companion object {
        private const val METRIC = "metric"

        fun from(payload: DataFrameEdgePayload): AggregationsTopkResponse {
            val topkItems =
                payload.edges.map { edge ->
                    TopkItem(
                        value = edge.target.toString(),
                        metric = (edge.properties[METRIC] as? Number)?.toLong() ?: 0L,
                        properties =
                            edge.properties
                                .filterKeys { it != METRIC }
                                .mapValues { (_, value) -> value.toString() },
                    )
                }
            return AggregationsTopkResponse(topks = topkItems, count = payload.count)
        }
    }
}
