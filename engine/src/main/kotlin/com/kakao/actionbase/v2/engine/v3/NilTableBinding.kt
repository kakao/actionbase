package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.binding.MutationRecordsSummary
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode

import reactor.core.publisher.Mono

class NilTableBinding(
    descriptor: V3TableDescriptor,
) : TableBinding {
    override val table: String = descriptor.table
    override val schema: ModelSchema = descriptor.schema
    override val mutationMode: MutationMode = MutationMode.SYNC

    override fun <T> withLock(
        key: MutationKey,
        action: () -> Mono<T>,
    ): Mono<T> = action()

    override fun read(key: MutationKey): Mono<State> = Mono.just(State.initial)

    override fun write(
        key: MutationKey,
        before: State,
        after: State,
    ): Mono<MutationRecordsSummary> = Mono.just(MutationRecordsSummary(IDLE, 0, State.initial, State.initial))

    override fun handleMutationError(error: Throwable) {}

    private companion object {
        const val IDLE = "IDLE"
    }
}
