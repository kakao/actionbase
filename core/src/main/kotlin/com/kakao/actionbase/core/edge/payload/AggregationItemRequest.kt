package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.payload.AggregationType

data class AggregationItemRequest(
    val type: AggregationType,
    val items: List<AggregationItemPayload>,
)

data class AggregationItemPayload(
    val database: String,
    val table: String,
    val edge: EdgePayload,
)
