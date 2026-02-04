package com.kakao.actionbase.server.api.graph.v3.metadata

data class AliasCreateRequest(
    val table: String,
    val comment: String = "",
)
