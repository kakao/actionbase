package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.payload.AggregationType

data class SweepRequest(
    val refreshDatabase: String,
    val refreshTable: String,
    val entries: List<SweepEntryPayload>,
)

data class SweepEntryPayload(
    val partition: Long,
    val key: String,
    val aggregation: SweepAggregationPayload,
)

data class SweepAggregationPayload(
    val type: AggregationType,
    val database: String,
    val table: String,
    val group: String,
    val topk: String,
    val direction: Direction,
    val edge: EdgePayload,
)
