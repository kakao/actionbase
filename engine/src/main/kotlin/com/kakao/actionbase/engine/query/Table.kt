package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.WherePredicate

import reactor.core.publisher.Mono

/**
 * V3 abstraction consumed by `ActionbaseQueryExecutor` to execute query items
 * (self / get / count / scan / cache) against a label. Used for both single-item
 * queries and multi-hop chains — multi-hop is just the case where a later item's
 * `Vertex.Ref` pulls values from a previous item's result.
 *
 * [TableBinding] is the REST-facing abstraction that returns `DataFrameEdge*Payload`
 * (with pagination metadata such as `offset`, `total`, `hasNext`). In contrast,
 * [Table] returns a flat, column-addressable [DataFrame] so that `Vertex.Ref`
 * resolution can use `df.getColumn(field)` for field-by-name access — which in
 * turn is what enables multi-hop chaining when it is needed.
 *
 * ## Column naming contract
 *
 * The [DataFrame] returned by every method of this interface MUST expose base columns
 * using v3 naming:
 *
 * | v3          | v2    | description        |
 * |-------------|-------|--------------------|
 * | `version`   | `ts`  | edge version       |
 * | `source`    | `src` | edge source vertex |
 * | `target`    | `tgt` | edge target vertex |
 * | `direction` | `dir` | edge direction     |
 *
 * User-defined property fields keep their original schema names.
 *
 * Implementations backed by v2 storage (`HBaseIndexedLabel`, etc.) are responsible
 * for translating v2 column names to v3 at the boundary. Callers of [Table] MUST
 * use v3 names only — in particular, `Vertex.Ref.field` in an `ActionbaseQuery`
 * chain must reference hop results by their v3 column names (e.g. `"target"`,
 * not `"tgt"`).
 */
interface Table {
    fun getSelf(
        sources: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun get(
        sources: List<Any>,
        targets: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun count(
        sources: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame>

    fun scan(
        sources: Set<Any>,
        direction: Direction,
        index: String,
        limit: Int?,
        offset: String?,
        predicates: List<WherePredicate>?,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
    ): Mono<DataFrame>
}
