package com.kakao.actionbase.server.api.graph.v3.metadata

/**
 * The v3 name for a v2 alias.
 *
 * Same shape as the v2 `AliasEntity` wire form, with `name` split into `database` and `alias`,
 * `target` narrowed to the table name within that database, and `desc` renamed to `comment`.
 */
data class AliasResponse(
    val active: Boolean,
    val database: String,
    val alias: String,
    val table: String,
    val comment: String,
)
