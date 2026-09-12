package com.kakao.actionbase.server.api.graph.v3.metadata

/**
 * The v3 name for what v2 calls a label type.
 *
 * One value per v2 [com.kakao.actionbase.v2.core.metadata.LabelType] that the v3 surface exposes.
 * v2's `HASH` and `NIL` have no v3 name; they read back as [EDGE] and cannot be created.
 */
enum class TableType {
    EDGE,
    IMMUTABLE_EDGE,
    MULTI_EDGE,
    VERTEX,
}
