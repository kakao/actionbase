package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeCountPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeCountPayload
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

class QueryService(
    private val engine: QueryEngine,
) {
    @Suppress("UnusedParameter")
    fun count(
        database: String,
        table: String,
        start: Any,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<EdgeCountPayload> =
        counts(database, table, listOf(start), direction, ranges, filters, features)
            .map {
                if (it.count == 0) {
                    empty(direction)
                } else {
                    it.counts.first()
                }
            }

    @Suppress("UnusedParameter")
    fun counts(
        database: String,
        table: String,
        start: List<Any>,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgeCountPayload> {
        require(ranges == null) { "`ranges` is not yet supported in count query." }
        require(filters == null) { "`filters` is not yet supported in count query." }
        require(features.isEmpty()) { "`features` ${features.joinToString(", ")} are not supported in get query." }

        return engine.getTableBinding(database, table).count(start.toSet(), direction)
    }

    @Suppress("UnusedParameter")
    fun gets(
        database: String,
        table: String,
        source: List<Any>,
        target: List<Any>,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        require(ranges == null) { "`ranges` is not supported in get query." }
        require(features.isEmpty()) { "`features` ${features.joinToString(", ")} are not supported in get query." }

        val keys =
            source.distinct().flatMap { s ->
                target.distinct().map { t -> s to t }
            }

        return engine.getTableBinding(database, table).gets(keys, filters)
    }

    @Suppress("UnusedParameter")
    fun gets(
        database: String,
        table: String,
        ids: List<Any>,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        require(features.isEmpty()) { "`features` ${features.joinToString(", ")} are not supported in get query." }

        val tb = engine.getTableBinding(database, table)

        require(tb.schema is com.kakao.actionbase.core.metadata.common.ModelSchema.MultiEdge) {
            "get query with ids is only supported for multi-edge tables."
        }

        val keys = ids.distinct().map { id -> id to id }
        return tb.gets(keys, filters)
    }

    fun scan(
        database: String,
        table: String,
        index: String,
        start: Any,
        direction: Direction,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> = engine.getTableBinding(database, table).scan(index, start, direction, limit, offset, ranges, filters, features)

    fun seek(
        database: String,
        table: String,
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> = engine.getTableBinding(database, table).seek(cache, start, direction, limit, offset)

    fun agg(
        database: String,
        table: String,
        group: String,
        start: List<Any>,
        direction: Direction,
        ranges: String,
        filters: String? = null,
        features: List<String> = emptyList(),
        ttl: Long? = null,
    ): Mono<DataFrameEdgeAggPayload> = engine.getTableBinding(database, table).agg(group, start, direction, ranges, filters, features, ttl)

    fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>> = engine.query(request)

    companion object {
        fun empty(direction: Direction): EdgeCountPayload =
            if (direction == Direction.OUT) {
                emptyEdgeCountPayloadOut
            } else {
                emptyEdgeCountPayloadIn
            }

        private val emptyEdgeCountPayloadOut: EdgeCountPayload =
            EdgeCountPayload(
                start = "",
                direction = com.kakao.actionbase.core.metadata.common.Direction.OUT,
                count = 0L,
                context = emptyMap(),
            )

        private val emptyEdgeCountPayloadIn: EdgeCountPayload =
            EdgeCountPayload(
                start = "",
                direction = com.kakao.actionbase.core.metadata.common.Direction.IN,
                count = 0L,
                context = emptyMap(),
            )
    }
}
