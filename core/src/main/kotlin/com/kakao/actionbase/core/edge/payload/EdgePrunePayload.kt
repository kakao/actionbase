package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.Direction

enum class PruneType {
    CACHE,
}

enum class PruneStatus {
    PRUNED,
    SKIPPED,
}

data class EdgePruneRequest(
    val type: PruneType,
    val targets: List<PruneTarget>,
)

data class PruneTarget(
    val start: Any,
    val direction: Direction,
)

/**
 * Prune response body — one [PruneResult] row per structure matched by a target.
 */
data class DataFramePrunePayload(
    val results: List<PruneResult>,
)

/** One prune result row, emitted per structure ([name]) matched by a [PruneTarget]. */
data class PruneResult(
    val start: Any,
    val direction: Direction,
    val type: PruneType,
    val name: String,
    val status: PruneStatus,
)
