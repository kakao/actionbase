package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType

fun LabelEntity.hasAggregation(type: AggregationType? = null): Boolean =
    groups.any { g ->
        if (type == null) {
            !g.aggregations.isEmpty()
        } else {
            g.aggregations.supports(type)
        }
    }

fun LabelEntity.toQualifiedAggregations(type: AggregationType? = null): List<QualifiedAggregations> {
    val candidateTypes = if (type != null) listOf(type) else AggregationType.entries
    val database = name.service
    val table = name.nameNotNull
    return candidateTypes
        .filter { t -> groups.any { g -> t.has(g.aggregations) } }
        .map { QualifiedAggregations(type = it, database = database, table = table) }
}
