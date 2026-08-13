package com.kakao.actionbase.core.edge.payload

data class AggregationSweepResult(
    val database: String,
    val table: String,
    val topk: String,
    val entity: String,
    val status: String,
    val error: String?,
)
