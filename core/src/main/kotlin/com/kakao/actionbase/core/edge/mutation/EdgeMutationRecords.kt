package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeIndexRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord

data class EdgeMutationRecords(
    val status: String,
    val acc: Long,
    val stateRecord: EdgeStateRecord,
    val createIndexRecords: List<EdgeIndexRecord> = emptyList(),
    val deleteIndexRecordKeys: List<EdgeIndexRecord.Key> = emptyList(),
    val countRecords: List<EdgeCountRecord> = emptyList(),
    val groupRecords: List<EdgeGroupRecord> = emptyList(),
    val createCacheRecords: List<EdgeCacheRecord> = emptyList(),
    val deleteCacheRecordQualifiers: List<Pair<EdgeCacheRecord.Key, EdgeCacheRecord.Qualifier>> = emptyList(),
)
