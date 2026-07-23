package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.AggregationType

data class AggregationSweepRequest(
    val items: List<AggregationSweepItem>,
)

data class AggregationSweepItem(
    val type: AggregationType,
    val item: AggregationSweepTarget,
)

data class AggregationSweepTarget(
    val database: String,
    val table: String,
    val topk: String,
    val source: String,
    val target: String,
    val direction: String,
    val ranges: String = "",
    val entity: String,
    val topkDimensionValue: String,
    val dimensionValues: String = "",
    val refreshAt: Long = -1,
)
