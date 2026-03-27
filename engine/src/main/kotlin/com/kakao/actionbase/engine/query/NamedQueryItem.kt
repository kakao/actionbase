package com.kakao.actionbase.engine.query

/**
 * V2-free representation of a named query result item.
 * Mirrors the serialized shape of [com.kakao.actionbase.v2.engine.sql.QueryResult.NamedJsonFormat]
 * without importing V2 types.
 */
data class NamedQueryItem(
    val name: String,
    val data: List<Map<String, Any?>>,
    val rows: Int,
    val stats: List<Any>,
    val offset: String?,
    val hasNext: Boolean,
)
