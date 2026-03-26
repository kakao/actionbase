package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.v2.engine.sql.QueryResult

data class NamedQueryResult(
    val items: List<QueryResult.NamedJsonFormat>,
)
