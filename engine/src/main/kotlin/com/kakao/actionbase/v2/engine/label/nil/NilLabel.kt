package com.kakao.actionbase.v2.engine.label.nil

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey

import reactor.core.publisher.Mono

class NilLabel(
    override val entity: LabelEntity,
) : Label {
    private val empty =
        Mono.just(
            DataFrame(
                rows = emptyList(),
                schema = entity.schema.allStructType,
            ),
        )

    private val emptyCount =
        Mono.just(
            DataFrame(
                rows = listOf(Row(arrayOf(0L))),
                schema = StructType(arrayOf(Field("COUNT(1)", DataType.LONG, false))),
            ),
        )

    override fun mutate(
        edges: List<TraceEdge>,
        op: EdgeOperation,
        alias: EntityName?,
        bulk: Boolean,
        failOnExist: Boolean,
        newCollector: () -> StorageOpCollector?,
    ): Mono<List<CdcContext>> =
        Mono.fromCallable {
            edges.map {
                CdcContext(
                    label = entity.name,
                    edge = it,
                    op = op,
                    status = EdgeOperationStatus.IDLE,
                    before = null,
                    after = null,
                    acc = 0L,
                )
            }
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> = empty

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> = empty

    override fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> = empty

    override fun count(
        src: Any,
        dir: Direction,
    ): Mono<DataFrame> = emptyCount

    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> = emptyCount

    override fun findStaleLockAndClear(
        lockEdge: KeyValue<Any>,
        lockTimeout: Long,
    ): Mono<Void> = Mono.empty()

    override fun close() {
        //
    }
}
