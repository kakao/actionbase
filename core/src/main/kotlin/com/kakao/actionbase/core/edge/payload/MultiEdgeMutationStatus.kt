package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.state.State

data class MultiEdgeMutationStatus(
    val id: Any,
    val count: Int,
    override val status: String, // CREATED, IDLE ...
    override val before: State,
    override val after: State,
    override val acc: Long,
) : MutationStatus {
    companion object {
        fun of(
            id: Any,
            count: Int,
            status: String,
        ) = MultiEdgeMutationStatus(id, count, status, State.initial, State.initial, 0)
    }
}
