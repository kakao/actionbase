package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.state.State

interface MutationStatus {
    val status: String
    val before: State
    val after: State
    val acc: Long
}
