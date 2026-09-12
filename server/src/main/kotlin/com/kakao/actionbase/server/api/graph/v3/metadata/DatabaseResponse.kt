package com.kakao.actionbase.server.api.graph.v3.metadata

/**
 * The v3 name for what v2 calls a service.
 *
 * Field-for-field the same shape as the v2 `ServiceEntity` wire form, with `name` split into
 * `database` and `desc` renamed to `comment`.
 */
data class DatabaseResponse(
    val active: Boolean,
    val database: String,
    val comment: String,
)
