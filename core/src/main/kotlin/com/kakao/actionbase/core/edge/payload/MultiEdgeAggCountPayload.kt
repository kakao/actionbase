package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.Direction

data class MultiEdgeAggCountPayload(
    val start: Any,
    val direction: Direction,
    val count: Long,
    val context: Map<String, Any?>,
)
