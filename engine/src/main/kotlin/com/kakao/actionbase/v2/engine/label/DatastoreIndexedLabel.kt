package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import reactor.core.publisher.Mono

class DatastoreIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    indices: List<Index>,
    indexNameToIndex: Map<String, Index>,
    buckets: Mono<StorageBuckets>,
) : HBaseIndexedLabel(entity, coder, indices, indexNameToIndex, buckets) {
    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreIndexedLabel.() -> Unit,
        ): DatastoreIndexedLabel {
            val indices = entity.indices
            val indexNameToIndex = indices.associateBy { it.name }
            val buckets = graph.datastore.getBucket(entity.storage).cache()
            return DatastoreIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToIndex,
                buckets = buckets,
            ).apply(initialize)
        }
    }
}
