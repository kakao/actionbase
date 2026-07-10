package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.payload.AggregationType

data class AggregationItemRequest(
    val type: AggregationType,
    val items: List<AggregationItemPayload>,
    // When true, this call is an expire-driven re-aggregation: refresh the score row only and do not
    // rewrite the expire row (the originating expire row was already deleted, so re-tracking would loop).
    val isExpire: Boolean = false,
)

data class AggregationItemPayload(
    val database: String,
    val table: String,
    val edge: EdgePayload,
    // When set, only the named topk is re-aggregated instead of every topk declared on the matching groups.
    // Used when replaying a stored item to refresh a single expired topk without recomputing its siblings.
    val topk: String? = null,
)
