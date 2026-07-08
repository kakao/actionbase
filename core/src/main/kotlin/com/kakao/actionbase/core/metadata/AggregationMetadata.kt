package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.metadata.common.Aggregations

data class AggregationMetadata(
    val database: String,
    val table: String,
    val aggregations: List<Aggregations>,
)
