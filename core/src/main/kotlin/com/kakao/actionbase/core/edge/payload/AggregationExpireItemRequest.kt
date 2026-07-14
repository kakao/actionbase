package com.kakao.actionbase.core.edge.payload

data class AggregationExpireItemRequest(
    val items: List<AggregationExpireItemPayload>,
)

data class AggregationExpireItemPayload(
    val database: String,
    val table: String,
    val edge: EdgePayload,
)
