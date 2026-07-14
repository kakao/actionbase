package com.kakao.actionbase.core.metadata.payload

data class ExpireTablesResponse(
    val tables: List<ExpireTableRef>,
)

data class ExpireTableRef(
    val database: String,
    val table: String,
)
