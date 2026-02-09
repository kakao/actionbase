package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.engine.storage.DatastoreUri
import com.kakao.actionbase.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.engine.storage.HBaseTablesProvider
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class DatastoreHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    tables: Mono<HBaseTables>,
) : HBaseHashLabel(entity, coder, tables) {
    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreHashLabel.() -> Unit,
        ): DatastoreHashLabel {
            val (ns, name) = DatastoreUri.parse(entity.storage)
            val effectiveNs = ns.ifEmpty { DefaultStorageBackendFactory.defaultNamespace }
            val provider =
                DefaultStorageBackendFactory.INSTANCE as? HBaseTablesProvider
                    ?: throw IllegalStateException("StorageBackend does not support HBaseTables")
            val tables = provider.getHBaseTables(effectiveNs, name).cache()
            return DatastoreHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = tables,
            ).apply(initialize)
        }
    }
}
