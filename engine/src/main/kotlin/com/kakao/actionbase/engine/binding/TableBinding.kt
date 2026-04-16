package com.kakao.actionbase.engine.binding

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.v2.core.metadata.Direction

import reactor.core.publisher.Mono

interface TableBinding {
    val table: String
    val schema: ModelSchema
    val mutationMode: MutationMode

    // -- mutation

    fun <T> withLock(
        key: MutationKey,
        action: () -> Mono<T>,
    ): Mono<T>

    fun read(key: MutationKey): Mono<State>

    fun write(
        key: MutationKey,
        before: State,
        after: State,
    ): Mono<MutationRecordsSummary>

    fun handleMutationError(error: Throwable)

    // -- query

    fun count(
        sources: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame>

    fun gets(
        keys: List<Pair<Any, Any>>,
        filters: String?,
    ): Mono<DataFrame>

    fun scan(
        index: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
        ranges: String?,
        filters: String?,
        features: List<String>,
    ): Mono<DataFrame>

    fun seek(
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
    ): Mono<DataFrame>

    fun agg(
        group: String,
        start: List<Any>,
        direction: Direction,
        ranges: String,
        filters: String?,
        features: List<String>,
        ttl: Long?,
    ): Mono<DataFrameEdgeAggPayload>
}

data class MutationRecordsSummary(
    val status: String,
    val acc: Long,
    val before: State,
    val after: State,
)
