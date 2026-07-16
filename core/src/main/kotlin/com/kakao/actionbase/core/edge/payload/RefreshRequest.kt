package com.kakao.actionbase.core.edge.payload

data class RefreshRequest(
    val entries: List<RefreshEntryPayload>,
)

data class RefreshEntriesResponse(
    val entries: List<RefreshEntryPayload>,
)

// (partition, key) is the refresh row's full coordinate: the key alone carries everything a
// refresh needs (see AggregationConstants.parseRefreshTarget), and partition is where the row
// lives, so no aggregation payload travels with the entry.
data class RefreshEntryPayload(
    val partition: Long,
    val key: String,
)
