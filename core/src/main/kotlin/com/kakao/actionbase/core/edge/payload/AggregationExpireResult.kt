package com.kakao.actionbase.core.edge.payload

data class AggregationExpireResult(
    val database: String,
    val table: String,
    val source: String,
    val status: String,
    val error: String?,
)
