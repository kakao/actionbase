package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.Dedupe
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.Topk

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
        .map { t ->
            QualifiedAggregations(
                type = t,
                database = database,
                table = table,
                dedupes = groups.flatMap { g -> g.aggregations.topk.map { g.dedupe(it) } },
            )
        }
}

/**
 * The flat dedupe key of one top-K: the directed entity endpoint (`source` for OUT, `target` for IN)
 * followed by the group's non-bucket fields. The endpoint isn't declared in meta — it's derived from
 * the group direction — but it's always part of the rank-row identity (the entity for a per-entity
 * ranking, the dimension for a global one), so it's always keyed on. Bucket fields are excluded; they
 * aren't part of the identity. BOTH is transitional (it produces two endpoints and will be rejected
 * at registration); until then it keys on source.
 */
private fun Group.dedupe(topk: Topk): Dedupe {
    val endpoint = if (directionType == DirectionType.IN) "target" else "source"
    val nonBucket = fields.filter { it.bucket == null }.map { it.name }
    return Dedupe(name = topk.topk, fields = listOf(endpoint) + nonBucket)
}
