package com.kakao.actionbase.server.payload

data class AggregationsTopkRequest(
    val entity: String? = null,
    val dimensionValues: Map<String, String> = emptyMap(),
    val limit: Int? = null,
    val offset: String? = null,
)
