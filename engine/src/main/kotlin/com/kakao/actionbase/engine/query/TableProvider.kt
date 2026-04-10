package com.kakao.actionbase.engine.query

import com.kakao.actionbase.v2.engine.entity.EntityName

/**
 * V3 lookup abstraction used by `ActionbaseQueryExecutor` to obtain a [Table]
 * for a given database/table identifier.
 *
 * This interface exists because `ActionbaseQueryExecutor` is constructed **inside**
 * the v2 `Graph` class before any v3 `QueryEngine` wrapper exists. Taking a
 * `QueryEngine` reference would create a construction-order cycle. Instead, a
 * v2 → v3 bridge (`V2BackedTableProvider`) implements [TableProvider] and is
 * injected into the executor via constructor injection.
 *
 * Replaces the legacy `LabelProvider`, which exposed v2 `Label` in a v3 namespace.
 */
interface TableProvider {
    fun getTable(name: EntityName): Table

    fun getTable(
        database: String,
        table: String,
    ): Table = getTable(EntityName(database, table))
}
