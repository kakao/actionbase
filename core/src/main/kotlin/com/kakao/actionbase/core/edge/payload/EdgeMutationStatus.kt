package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.state.State

data class EdgeMutationStatus(
    val source: Any,
    val target: Any,
    val count: Int,
    override val status: String, // CREATED, IDLE ...
    override val before: State,
    override val after: State,
    override val acc: Long,
) : MutationStatus
