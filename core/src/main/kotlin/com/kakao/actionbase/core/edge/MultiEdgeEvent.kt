package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.state.Event

data class MultiEdgeEvent(
    override val id: Any,
    override val event: Event,
) : MutationEvent<Any>
