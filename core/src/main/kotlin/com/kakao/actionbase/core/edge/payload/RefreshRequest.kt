package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.Direction

data class RefreshRequest(
    val entries: List<RefreshEntryPayload>,
)

data class RefreshEntriesResponse(
    val entries: List<RefreshEntryPayload>,
)

// A refresh row's key, parsed into its components (see AggregationConstants.parseRefreshTarget).
// This is the full refresh coordinate: it names the topk to re-aggregate, the score row to
// rewrite, and — rebuilt through the key builders — the refresh row to delete afterwards.
data class RefreshEntryPayload(
    val database: String,
    val table: String,
    val topk: String,
    val direction: Direction,
    val entity: String,
    val segment: String? = null,
    val rankedField: String,
    val refreshAt: Long,
)
