package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class DatastoreHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    tables: Mono<HBaseTables>,
) : HBaseHashLabel(entity, coder, tables) {
    companion object {
        @Suppress("DEPRECATION")
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreHashLabel.() -> Unit,
        ): DatastoreHashLabel {
            val tables = DefaultStorageBackendFactory.INSTANCE.getTable(entity.storage).cache()
            return DatastoreHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = tables,
            ).apply(initialize)
        }
    }
}
