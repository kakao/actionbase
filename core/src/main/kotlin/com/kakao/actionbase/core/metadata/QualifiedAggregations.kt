package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.metadata.common.AggregationType

data class QualifiedAggregations(
    val type: AggregationType,
    val database: String,
    val table: String,
)
