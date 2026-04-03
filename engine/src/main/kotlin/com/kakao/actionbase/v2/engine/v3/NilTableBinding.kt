package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeCountPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.binding.MutationRecordsSummary
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.v2.core.metadata.Direction

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

    override fun count(
        sources: Set<Any>,
        direction: Direction,
    ): Mono<DataFrameEdgeCountPayload> = Mono.just(DataFrameEdgeCountPayload(emptyList(), 0, emptyMap()))

    override fun gets(
        keys: List<Pair<Any, Any>>,
        filters: String?,
    ): Mono<DataFrameEdgePayload> = V2BackedTableBinding.EMPTY_EDGE_PAYLOAD

    override fun scan(
        index: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
        ranges: String?,
        filters: String?,
        features: List<String>,
    ): Mono<DataFrameEdgePayload> = V2BackedTableBinding.EMPTY_EDGE_PAYLOAD

    override fun seek(
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
    ): Mono<DataFrameEdgePayload> = V2BackedTableBinding.EMPTY_EDGE_PAYLOAD

    override fun agg(
        group: String,
        start: List<Any>,
        direction: Direction,
        ranges: String,
        filters: String?,
        features: List<String>,
        ttl: Long?,
    ): Mono<DataFrameEdgeAggPayload> = Mono.just(DataFrameEdgeAggPayload(emptyList(), 0, emptyMap()))

    private companion object {
        const val IDLE = "IDLE"
    }
}
