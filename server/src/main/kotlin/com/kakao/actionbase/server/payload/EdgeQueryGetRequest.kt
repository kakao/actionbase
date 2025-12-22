package com.kakao.actionbase.server.payload

data class EdgeQueryGetRequest(
    val source: List<String>,
    val target: List<String>,
    val ranges: String? = null,
    val filters: String? = null,
    val features: List<String> = emptyList(),
)
