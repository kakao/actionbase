package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

/** Hash label exposed as an IndexedLabel to keep the V3 transition simple. */
class HBaseHashOnlyIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    tables: Mono<HBaseTables>,
    edgeRecordMapper: EdgeRecordMapper,
    lockTimeout: Long,
) : HBaseIndexedLabel(
        entity = entity,
        coder = coder,
        indices = emptyList(),
        indexNameToIndex = emptyMap(),
        tables = tables,
        edgeRecordMapper = edgeRecordMapper,
        lockTimeout = lockTimeout,
    ) {
    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> = unsupported("scan")

    override fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
        offset: String?,
    ): Mono<DataFrame> = unsupported("seek")

    private fun unsupported(op: String): Mono<DataFrame> =
        Mono.error(
            UnsupportedOperationException("$op is not supported on HASH-type label '${entity.fullName}'"),
        )

    companion object : LabelFactory<HBaseHashOnlyIndexedLabel, HBaseStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: HBaseStorage,
            block: HBaseHashOnlyIndexedLabel.() -> Unit,
        ): HBaseHashOnlyIndexedLabel =
            HBaseHashOnlyIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = storage.options.getTables(),
                edgeRecordMapper = graph.edgeRecordMapper,
                lockTimeout = graph.lockTimeout,
            )
    }
}
