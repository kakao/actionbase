package com.kakao.actionbase.engine

import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeCountPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeCountPayload
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.NamedQueryItem

import reactor.core.publisher.Mono

/**
 * Query engine interface for edge queries.
 * Uses V3 types in the public API to decouple controllers from V2 internals.
 */
interface QueryEngine {
    fun count(
        database: String,
        table: String,
        start: Any,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<EdgeCountPayload>

    fun counts(
        database: String,
        table: String,
        start: List<Any>,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgeCountPayload>

    fun gets(
        database: String,
        table: String,
        source: List<Any>,
        target: List<Any>,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload>

    /**
     * Overloaded gets for multi-edge tables using ids.
     */
    fun gets(
        database: String,
        table: String,
        ids: List<Any>,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload>

    fun scan(
        database: String,
        table: String,
        index: String,
        start: Any,
        direction: Direction,
        limit: Int = DEFAULT_LIMIT,
        offset: String? = null,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload>

    fun cache(
        database: String,
        table: String,
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int = DEFAULT_LIMIT,
    ): Mono<DataFrameEdgePayload>

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
    ): Mono<DataFrameEdgeAggPayload>

    fun query(request: ActionbaseQuery): Mono<List<NamedQueryItem>>

    companion object {
        const val DEFAULT_LIMIT: Int = 10
    }
}
