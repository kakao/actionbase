package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.state.Event

data class MultiEdgeEvent(
    val id: Any,
    override val event: Event,
) : MutationEvent {
    override val key: MutationKey = MutationKey.Id(id)
}
