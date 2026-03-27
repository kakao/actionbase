package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.engine.query.NamedQueryItem

data class NamedQueryResult(
    val items: List<NamedQueryItem>,
)
