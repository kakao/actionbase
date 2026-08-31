package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.AggregationConstants

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

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
        private val MAPPER = jacksonObjectMapper()

        fun from(payload: DataFrameEdgePayload): AggregationsTopkResponse {
            val topkItems =
                payload.edges.map { edge ->
                    TopkItem(
                        value = edge.target.toString(),
                        metric = (edge.properties[AggregationConstants.Topk.METRIC] as? Number)?.toLong() ?: 0L,
                        properties =
                            (edge.properties[AggregationConstants.Topk.ADDITIONAL_PROPERTIES] as? String)
                                ?.let { MAPPER.readValue<Map<String, String>>(it) }
                                ?: emptyMap(),
                    )
                }
            return AggregationsTopkResponse(topks = topkItems, count = payload.count)
        }
    }
}
