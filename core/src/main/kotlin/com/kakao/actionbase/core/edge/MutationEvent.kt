package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event

sealed interface MutationKey {
    data class SourceTarget(
        val source: Any,
        val target: Any,
    ) : MutationKey

    data class Id(
        val id: Any,
    ) : MutationKey
}

sealed interface MutationEvent {
    val key: MutationKey
    val event: Event
}

interface UnresolvedEvent {
    fun createEvent(schema: ModelSchema): MutationEvent
}
