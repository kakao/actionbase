package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group

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
    return candidateTypes
        .filter { t -> groups.any { g -> t.has(g.aggregations) } }
        .map { t ->
            QualifiedAggregations(
                type = t,
                database = name.service,
                table = name.nameNotNull,
                dedupeFields = groups.filter { g -> t.has(g.aggregations) }.flatMap { it.dedupeFields() }.distinct(),
            )
        }
}

// The endpoint is derived from the group direction rather than declared, but it is part of the rank row's
// identity either way, so it is always keyed on. Buckets are not identity. BOTH would produce two
// endpoints; it keys on source until something rejects it.
private fun Group.dedupeFields(): List<String> {
    val endpoint = if (directionType == DirectionType.IN) "target" else "source"
    return listOf(endpoint) + fields.filter { it.bucket == null }.map { it.name }
}
