package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

class DatastoreHashOnlyIndexedLabel(
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
    ): Mono<DataFrame> = unsupported("scan")

    override fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
        offset: String?,
        predicates: List<WherePredicate>,
    ): Mono<DataFrame> = unsupported("seek")

    private fun unsupported(op: String): Mono<DataFrame> =
        Mono.error(
            UnsupportedOperationException("$op is not supported on HASH-type label '${entity.fullName}'"),
        )

    companion object {
        fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            initialize: DatastoreHashOnlyIndexedLabel.() -> Unit,
        ): DatastoreHashOnlyIndexedLabel {
            val tables = graph.datastore.getTable(entity.storage).cache()
            return DatastoreHashOnlyIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = tables,
                edgeRecordMapper = graph.edgeRecordMapper,
                lockTimeout = graph.lockTimeout,
            ).apply(initialize)
        }
    }
}
