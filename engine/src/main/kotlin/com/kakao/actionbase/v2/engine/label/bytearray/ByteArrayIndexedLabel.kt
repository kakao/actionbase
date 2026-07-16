package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.mixin.IndexedLabelMixin
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey

import reactor.core.publisher.Mono

open class ByteArrayIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    override val indices: List<Index>,
    override val indexNameToIndex: Map<String, Index>,
    store: ByteArrayStore,
) : ByteArrayHashLabel(entity, coder, store),
    IndexedLabelMixin<ByteArray> {
    override val self: AbstractLabel<ByteArray> = this

    override fun finalizeEdgeMutationUnderLock(context: CdcContext): Mono<List<Any>> = mutateIndexedEdges(context)

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> = scanIndexedEdges(scanFilter, stats)

    companion object {
        fun create(
            entity: LabelEntity,
            coder: EdgeEncoder<ByteArray>,
            store: ByteArrayStore,
        ): ByteArrayIndexedLabel =
            ByteArrayIndexedLabel(
                entity = entity,
                coder = coder,
                indices = entity.indices,
                indexNameToIndex = entity.indices.associateBy { it.name },
                store = store,
            )
    }
}
