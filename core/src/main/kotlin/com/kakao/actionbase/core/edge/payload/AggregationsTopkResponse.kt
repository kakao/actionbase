package com.kakao.actionbase.core.edge.payload

/**
 * Top-K query response: the ranked values for one `(topk, entity, dimensionValues)` in descending
 * metric order. Each rank row's `target` is the ranked value, its `metric` property is the score, and
 * any remaining properties are the extra fields the top-K declared to carry.
 */
data class AggregationsTopkResponse(
    val topks: List<Rank>,
    val count: Int,
) {
    data class Rank(
        val value: String,
        val metric: Long,
        val properties: Map<String, String>,
    )

    companion object {
        private const val METRIC = "metric"

        fun from(payload: DataFrameEdgePayload): AggregationsTopkResponse {
            val ranks =
                payload.edges.map { edge ->
                    Rank(
                        value = edge.target.toString(),
                        metric = (edge.properties[METRIC] as? Number)?.toLong() ?: 0L,
                        properties =
                            edge.properties
                                .filterKeys { it != METRIC }
                                .mapValues { (_, value) -> value.toString() },
                    )
                }
            return AggregationsTopkResponse(topks = ranks, count = payload.count)
        }
    }
}
