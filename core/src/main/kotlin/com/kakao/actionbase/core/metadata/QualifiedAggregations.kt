package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.metadata.common.AggregationType

data class QualifiedAggregations(
    val type: AggregationType,
    val database: String,
    val table: String,
    val dedupes: List<Dedupe> = emptyList(),
)

/**
 * The dedupe strategy for one aggregation, so a streaming consumer can collapse a mini-batch before
 * it aggregates. Key each event by resolving every name in [fields] against it, join them, and drop
 * repeats to leave one representative per rank row.
 *
 * [fields] already folds in the entity endpoint (`source`/`target`) for a per-entity ranking; a
 * global ranking contributes only its non-bucket group fields. Bucket fields are excluded because
 * they aren't part of the rank-row identity.
 */
data class Dedupe(
    val name: String,
    val fields: List<String>,
)
