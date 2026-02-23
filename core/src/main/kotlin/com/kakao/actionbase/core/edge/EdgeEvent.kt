package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.state.Event

data class EdgeEvent(
    val source: Any,
    val target: Any,
    override val event: Event,
) : MutationEvent {
    override val key: MutationKey = MutationKey.SourceTarget(source, target)
}
