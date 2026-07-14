package com.kakao.actionbase.core.metadata.payload

data class RefreshTablesResponse(
    val tables: List<RefreshTableRef>,
)

data class RefreshTableRef(
    val database: String,
    val table: String,
)
