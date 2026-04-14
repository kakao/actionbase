package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.DatabaseId
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.TableId
import com.kakao.actionbase.engine.Engine

/**
 * The engine's registry of databases, tables, and aliases — the thing
 * Spark, Iceberg, Trino, and Glue all call a "catalog".
 *
 * A `Catalog` is bound to its [Engine] at construction time so it can
 * read metadata that is itself stored as Actionbase data — the same
 * self-hosted pattern as the v2 `Graph`. [Engine] depends on this
 * interface only and never on a concrete implementation.
 *
 * ## Lifecycle
 * - [bind] is called exactly once, from [Engine]'s constructor. The
 *   engine is not yet fully constructed at that point, so implementations
 *   must not call back into it synchronously.
 * - [close] is called on shutdown. Implementations must be idempotent —
 *   `close` may be called multiple times and must not throw.
 * - Both methods may assume single-threaded invocation by the engine.
 *
 * ## Reading
 * - [databases], [tables], and [aliases] may be read from any thread at
 *   any time after construction. Implementations must return maps that
 *   do not change from the caller's perspective — either immutable maps
 *   or snapshot-at-read views. A single getter call returns a consistent
 *   map; cross-map consistency (reading two getters back to back) is not
 *   guaranteed across reloads but is effectively atomic in practice.
 */
interface Catalog : AutoCloseable {
    fun bind(engine: Engine)

    val databases: Map<DatabaseId, DatabaseDescriptor>

    val tables: Map<TableId, TableDescriptor<*>>

    val aliases: Map<TableId, AliasDescriptor>
}
