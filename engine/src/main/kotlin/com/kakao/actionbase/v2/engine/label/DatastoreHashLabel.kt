package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.storage.StorageBuckets

import reactor.core.publisher.Mono

class DatastoreHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    buckets: Mono<StorageBuckets>,
) : HBaseHashLabel(entity, coder, buckets) {
    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreHashLabel.() -> Unit,
        ): DatastoreHashLabel {
            val buckets = graph.datastore.getBucket(entity.storage).cache()
            return DatastoreHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                buckets = buckets,
            ).apply(initialize)
        }
    }
}
