package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.engine.storage.DatastoreUri
import com.kakao.actionbase.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.engine.storage.HBaseTablesProvider
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class HBaseStorageBackendIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    indices: List<Index>,
    indexNameToIndex: Map<String, Index>,
    tables: Mono<HBaseTables>,
) : HBaseIndexedLabel(entity, coder, indices, indexNameToIndex, tables) {
    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: HBaseStorageBackendIndexedLabel.() -> Unit,
        ): HBaseStorageBackendIndexedLabel {
            val indices = entity.indices
            val indexNameToIndex = indices.associateBy { it.name }
            val (ns, name) = DatastoreUri.parse(entity.storage)
            val effectiveNs = ns.ifEmpty { DefaultStorageBackendFactory.defaultNamespace }
            val provider =
                DefaultStorageBackendFactory.INSTANCE as? HBaseTablesProvider
                    ?: throw IllegalStateException("StorageBackend does not support HBaseTables")
            val tables = provider.getHBaseTables(effectiveNs, name).cache()
            return HBaseStorageBackendIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToIndex,
                tables = tables,
            ).apply(initialize)
        }
    }
}
