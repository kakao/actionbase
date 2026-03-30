package com.kakao.actionbase.engine.binding

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.StatKey

import reactor.core.publisher.Mono

interface TableBinding {
    val table: String
    val schema: ModelSchema
    val mutationMode: MutationMode

    // -- mutation --

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

    // -- query --

    fun get(
        source: List<Any>,
        target: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun count(
        source: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame>

    fun scan(
        filter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame>
}

data class MutationRecordsSummary(
    val status: String,
    val acc: Long,
    val before: State,
    val after: State,
)
