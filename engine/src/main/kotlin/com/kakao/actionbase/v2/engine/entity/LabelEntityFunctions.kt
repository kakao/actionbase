package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations

fun LabelEntity.hasAggregation(type: AggregationType? = null): Boolean = groups.any { it.aggregations.supports(type) }

private fun Aggregations.supports(type: AggregationType?): Boolean = if (type == null) !isEmpty() else supportedTypes.contains(type)

fun LabelEntity.toQualifiedAggregations(type: AggregationType? = null): List<QualifiedAggregations> {
    val database = name.service
    val table = name.nameNotNull

    return groups
        .flatMap { it.aggregations.supportedTypes }
        .toSet()
        .filter { type == null || it == type }
        .map { QualifiedAggregations(type = it, database = database, table = table) }
}
