package com.kakao.actionbase.engine.binding

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.metadata.MutationMode

import reactor.core.publisher.Mono

interface TableBinding {
    val table: String
    val schema: ModelSchema
    val mutationMode: MutationMode

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
}

data class MutationRecordsSummary(
    val status: String,
    val acc: Long,
    val before: State,
    val after: State,
)
