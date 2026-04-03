package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.engine.binding.TableBinding
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
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables
import com.kakao.actionbase.v2.engine.v3.V2BackedTableBinding
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

import reactor.core.publisher.Mono

/**
 * Manages IndexedEdgeEncoder in HBase
 */
open class HBaseIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    override val indices: List<Index>,
    override val indexNameToIndex: Map<String, Index>,
    tables: Mono<HBaseTables>,
    edgeRecordMapper: EdgeRecordMapper,
    lockTimeout: Long,
) : HBaseHashLabel(
        entity = entity,
        coder = coder,
        tables = tables,
    ),
    IndexedLabelMixin<ByteArray> {
    val tableBinding: TableBinding =
        V2BackedTableBinding(
            descriptor = V3TableDescriptor.create(entity),
            label = this,
            mapper = edgeRecordMapper,
            lockTimeout = lockTimeout,
        )

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

    companion object : LabelFactory<HBaseIndexedLabel, HBaseStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: HBaseStorage,
            block: HBaseIndexedLabel.() -> Unit,
        ): HBaseIndexedLabel {
            val tables = storage.options.getTables()
            val indices: List<Index> = entity.indices
            val indexNameToId = indices.associateBy { it.name }
            return HBaseIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToId,
                tables = tables,
                edgeRecordMapper = graph.edgeRecordMapper,
                lockTimeout = graph.lockTimeout,
            )
        }
    }
}
