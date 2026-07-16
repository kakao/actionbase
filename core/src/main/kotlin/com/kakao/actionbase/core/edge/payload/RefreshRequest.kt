package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Direction

data class RefreshRequest(
    val entries: List<RefreshEntryPayload>,
)

data class RefreshEntriesResponse(
    val entries: List<RefreshEntryPayload>,
)

data class RefreshEntryPayload(
    val partition: Long,
    val key: String,
    val aggregation: RefreshAggregationPayload,
)

data class RefreshAggregationPayload(
    val type: AggregationType,
    val database: String,
    val table: String,
    val group: String,
    val topk: String,
    val direction: Direction,
    val edge: EdgePayload,
)
