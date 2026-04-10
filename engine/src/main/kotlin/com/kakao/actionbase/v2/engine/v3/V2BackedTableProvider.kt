package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.engine.query.Table
import com.kakao.actionbase.engine.query.TableProvider
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName

/**
 * v2 → v3 bridge that exposes a [TableProvider] backed by a [Graph]. This is
 * the entry point `ActionbaseQueryExecutor` uses to obtain a [Table] for any
 * query item (self / get / count / scan / cache), so it does not enforce any
 * specific label subtype — whatever v2 [Graph.getLabel] returns is wrapped as
 * a [V2BackedTable], and capability mismatches (e.g. scan against an unindexed
 * label) surface as runtime errors from the underlying v2 side.
 *
 * Resolves an [EntityName] through [Graph.getLabel] (which handles alias redirection)
 * and wraps the resulting v2 label in a [V2BackedTable].
 *
 * This class sits in `v2/engine/v3/` alongside the other v2→v3 bridge classes
 * ([V2BackedEngine], [V2BackedTableBinding], [V2BackedTable], [V2BackedMessageBinding])
 * to keep all bridging logic in a single location. The v2 [Graph] itself remains
 * agnostic of v3 types.
 */
class V2BackedTableProvider(
    private val graph: Graph,
) : TableProvider {
    override fun getTable(name: EntityName): Table = V2BackedTable(label = graph.getLabel(name))
}
