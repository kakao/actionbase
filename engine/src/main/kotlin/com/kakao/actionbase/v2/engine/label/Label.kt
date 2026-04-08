package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey

import java.lang.AutoCloseable

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface Label : AutoCloseable {
    val entity: LabelEntity

    val name: EntityName
        get() = entity.name

    fun mutate(
        edges: List<TraceEdge>,
        op: EdgeOperation,
        alias: EntityName? = null,
        bulk: Boolean = false,
        failOnExist: Boolean = false,
    ): Mono<List<CdcContext>>

    fun mutate(
        edge: TraceEdge,
        op: EdgeOperation,
        alias: EntityName? = null,
        bulk: Boolean = false,
        failOnExist: Boolean = false,
    ): Mono<CdcContext> = mutate(listOf(edge), op, alias = alias, bulk = bulk, failOnExist = failOnExist).map { it.first() }

    fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame>

    fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame>

    fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> = get(src, listOf(tgt), dir, stats, idEdgeEncoder)

    fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> =
        Flux
            .fromIterable(tgt)
            .flatMap { get(src, it, dir, stats, idEdgeEncoder) }
            .filter { it.rows.isNotEmpty() }
            .reduce { a, b -> a + b }

    fun get(
        src: List<Any>,
        tgt: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> =
        Flux
            .fromIterable(src)
            .flatMap { get(it, tgt, Direction.OUT, stats, idEdgeEncoder) }
            .filter { it.rows.isNotEmpty() }
            .reduce { a, b -> a + b }

    fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
    ): Mono<DataFrame> = Mono.error(UnsupportedOperationException("cache is not supported for ${this::class.simpleName}"))

    fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame>

    fun count(
        src: Any,
        dir: Direction,
    ): Mono<DataFrame> = count(setOf(src), dir)

    fun status(): Mono<String> = Mono.just("N/A")

    fun findStaleLockAndClear(
        lockEdge: KeyValue<Any>,
        lockTimeout: Long,
    ): Mono<Void>

    fun getEdgeId(
        idEdgeEncoder: IdEdgeEncoder,
        src: Any,
        tgt: Any,
    ): String {
        val castedSrc =
            entity.schema.src.dataType
                .cast(src)
        val castedTgt =
            entity.schema.tgt.dataType
                .cast(tgt)
        return idEdgeEncoder.encode(castedSrc, castedTgt)
    }
}
