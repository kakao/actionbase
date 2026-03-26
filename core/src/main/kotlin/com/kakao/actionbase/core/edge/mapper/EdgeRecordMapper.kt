package com.kakao.actionbase.core.edge.mapper

data class EdgeRecordMapper(
    val state: EdgeStateRecordMapper,
    val index: EdgeIndexRecordMapper,
    val count: EdgeCountRecordMapper,
    val lock: EdgeLockRecordMapper,
    val group: EdgeGroupRecordMapper,
    val cache: EdgeCacheRecordMapper,
)
