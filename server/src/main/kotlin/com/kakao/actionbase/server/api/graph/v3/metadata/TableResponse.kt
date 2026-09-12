package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.MutationMode

/**
 * The v3 name for what v2 calls a label.
 *
 * Field for field the v2 `LabelEntity` wire form: `name` split into `database` and `table`,
 * `desc` renamed to `comment`, `dirType` to `direction`, `indices` to `indexes`, and the v2-only
 * `event` / `readOnly` flags dropped (the server derives them).
 */
data class TableResponse(
    val active: Boolean,
    val database: String,
    val table: String,
    val comment: String,
    val type: TableType,
    val schema: TableSchema,
    val direction: DirectionType,
    val storage: String,
    val indexes: List<Index> = emptyList(),
    val groups: List<Group> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val mode: MutationMode = MutationMode.SYNC,
)
