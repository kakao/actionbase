package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.payload.AggregationType

data class RefreshRequest(
    val refreshDatabase: String,
    val refreshTable: String,
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
