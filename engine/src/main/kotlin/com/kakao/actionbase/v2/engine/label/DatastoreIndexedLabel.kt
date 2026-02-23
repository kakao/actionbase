package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class DatastoreIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    indices: List<Index>,
    indexNameToIndex: Map<String, Index>,
    tables: Mono<HBaseTables>,
    edgeRecordMapper: EdgeRecordMapper,
    lockTimeout: Long,
) : HBaseIndexedLabel(entity, coder, indices, indexNameToIndex, tables, edgeRecordMapper, lockTimeout) {
    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreIndexedLabel.() -> Unit,
        ): DatastoreIndexedLabel {
            val indices = entity.indices
            val indexNameToIndex = indices.associateBy { it.name }
            val tables = graph.datastore.getTable(entity.storage).cache()
            return DatastoreIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToIndex,
                tables = tables,
                edgeRecordMapper = graph.edgeRecordMapper,
                lockTimeout = graph.lockTimeout,
            ).apply(initialize)
        }
    }
}
