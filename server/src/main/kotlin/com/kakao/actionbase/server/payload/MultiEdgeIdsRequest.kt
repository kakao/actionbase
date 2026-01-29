package com.kakao.actionbase.server.payload

data class MultiEdgeIdsRequest(
    val ids: List<Any>,
    val filters: String? = null,
    val features: List<String> = emptyList(),
)
