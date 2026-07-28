package com.kakao.actionbase.core.edge.payload

data class AggregationItemRequest(
    val items: List<AggregationItemPayload>,
)

data class AggregationItemPayload(
    val database: String,
    val table: String,
    val edge: EdgePayload,
)
