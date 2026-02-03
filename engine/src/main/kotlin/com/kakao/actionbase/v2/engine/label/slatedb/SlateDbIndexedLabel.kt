package com.kakao.actionbase.v2.engine.label.slatedb

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.label.mixin.IndexedLabelMixin
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbStorage
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbTable

import reactor.core.publisher.Mono

/**
 * Manages IndexedEdgeEncoder in SlateDB
 */
class SlateDbIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    override val indices: List<Index>,
    override val indexNameToIndex: Map<String, Index>,
    table: Mono<SlateDbTable>,
) : SlateDbHashLabel(
        entity = entity,
        coder = coder,
        table = table,
    ),
    IndexedLabelMixin<ByteArray> {
    override val self: AbstractLabel<ByteArray> = this

    override fun finalizeEdgeMutationUnderLock(context: CdcContext): Mono<List<Any>> = mutateIndexedEdges(context)

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> =
        scanIndexedEdges(
            scanFilter,
            stats,
            idEdgeEncoder,
        )

    companion object : LabelFactory<SlateDbIndexedLabel, SlateDbStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: SlateDbStorage,
            block: SlateDbIndexedLabel.() -> Unit,
        ): SlateDbIndexedLabel {
            val table = storage.options.getTable()
            val indices: List<Index> = entity.indices
            val indexNameToId = indices.associateBy { it.name }
            return SlateDbIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToId,
                table = table,
            )
        }
    }
}
