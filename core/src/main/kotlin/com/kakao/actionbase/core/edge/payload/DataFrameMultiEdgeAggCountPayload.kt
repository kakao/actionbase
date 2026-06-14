package com.kakao.actionbase.core.edge.payload

data class DataFrameMultiEdgeAggCountPayload(
    val counts: List<MultiEdgeAggCountPayload>,
    val count: Int,
    val context: Map<String, Any?>,
)
