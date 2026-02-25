package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.state.State

data class MutationResult(
    val key: MutationKey,
    val count: Int,
    val status: String,
    val before: State = State.initial,
    val after: State = State.initial,
    val acc: Long = 0,
) {
    companion object {
        fun of(
            key: MutationKey,
            count: Int,
            status: String,
        ) = MutationResult(key, count, status)
    }
}
