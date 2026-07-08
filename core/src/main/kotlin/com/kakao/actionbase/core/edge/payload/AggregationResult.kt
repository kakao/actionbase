package com.kakao.actionbase.core.edge.payload

data class AggregationResult(
    val database: String,
    val table: String,
    val source: String,
    val target: String,
    val status: String,
    val error: String?,
)
