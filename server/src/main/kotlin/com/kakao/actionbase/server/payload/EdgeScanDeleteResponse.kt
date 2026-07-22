package com.kakao.actionbase.server.payload

data class EdgeScanDeleteResponse(
    val database: String,
    val table: String,
    val index: String,
    val deleted: Int,
)
