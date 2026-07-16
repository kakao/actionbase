package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationSystemTables
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations

fun LabelEntity.hasAggregation(type: AggregationType? = null): Boolean = groups.any { it.aggregations.supports(type) }

fun LabelEntity.isSystemTable(): Boolean = AggregationSystemTables.contains(database = name.service, table = name.nameNotNull)

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

fun LabelEntity.toSystemQualifiedAggregations(type: AggregationType? = null): List<QualifiedAggregations> {
    val systemType = AggregationSystemTables.typeOf(database = name.service, table = name.nameNotNull) ?: return emptyList()
    if (type != null && type != systemType) return emptyList()
    return listOf(QualifiedAggregations(type = systemType, database = name.service, table = name.nameNotNull, refresh = true))
}
