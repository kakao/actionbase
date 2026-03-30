package com.kakao.actionbase.engine.binding

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.sql.WherePredicate

data class ScanFilter(
    val sourceSet: Set<Any>,
    val direction: Direction,
    val limit: Int,
    val offset: String? = null,
    val indexName: String,
    val predicates: Set<WherePredicate> = emptySet(),
)
