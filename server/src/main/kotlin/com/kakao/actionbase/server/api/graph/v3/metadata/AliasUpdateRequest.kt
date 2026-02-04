package com.kakao.actionbase.server.api.graph.v3.metadata

data class AliasUpdateRequest(
    val active: Boolean? = null,
    val table: String? = null,
    val comment: String? = null,
)
