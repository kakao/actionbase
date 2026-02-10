package com.kakao.actionbase.core.edge

import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event

sealed interface MutationEvent<K : Any> {
    val id: K
    val event: Event

    interface Source<out E : MutationEvent<*>> {
        fun createEvent(schema: ModelSchema): E
    }
}
