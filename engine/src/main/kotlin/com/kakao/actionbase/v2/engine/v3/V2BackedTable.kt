package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.SystemProperties
import com.kakao.actionbase.engine.query.Table
import com.kakao.actionbase.v2.core.code.EmptyEdgeIdEncoder
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.WherePredicate

import reactor.core.publisher.Mono

/**
 * v2-backed [Table] implementation that wraps a v2 [Label]. Serves every
 * [Table] operation used by `ActionbaseQueryExecutor`, whether the query is a
 * single item or part of a multi-hop chain.
 *
 * Each query method delegates to the corresponding v2 [Label] operation and then
 * renames base edge columns from v2 internal names (`ts/src/tgt/dir`) to v3 public
 * names (`version/source/target/direction`) per the [Table] contract. User-defined
 * property fields pass through unchanged.
 *
 * Query paths never need to encode edge IDs, so [EmptyEdgeIdEncoder.INSTANCE] is
 * passed directly wherever an `IdEdgeEncoder` parameter is required.
 *
 * Only depends on the [Label] interface — there is no compile-time guarantee that
 * the underlying label supports every [Table] operation (e.g. scan-by-index).
 * Labels lacking a required capability surface a runtime error from the v2 side.
 */
class V2BackedTable(
    private val label: Label,
) : Table {
    override fun getSelf(
        sources: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> =
        label
            .getSelf(sources, stats, EmptyEdgeIdEncoder.INSTANCE)
            .map(::toV3)

    override fun get(
        sources: List<Any>,
        targets: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> =
        label
            .get(sources, targets, stats, EmptyEdgeIdEncoder.INSTANCE)
            .map(::toV3)

    override fun count(
        sources: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame> =
        label
            .count(sources, direction.toV2())
            .map(::toV3)

    override fun scan(
        sources: Set<Any>,
        direction: Direction,
        index: String,
        limit: Int?,
        offset: String?,
        predicates: List<WherePredicate>?,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val scanFilter =
            ScanFilter(
                name = label.entity.name,
                srcSet = sources,
                dir = direction.toV2(),
                limit = limit ?: ScanFilter.defaultLimit,
                offset = offset,
                indexName = index,
                otherPredicates = predicates?.toSet() ?: emptySet(),
            )
        return label
            .scan(scanFilter, stats, EmptyEdgeIdEncoder.INSTANCE)
            .map(::toV3)
    }

    override fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
    ): Mono<DataFrame> =
        label
            .cache(sources, cacheName, direction.toV2(), limit)
            .map(::toV3)

    private fun Direction.toV2(): com.kakao.actionbase.v2.core.metadata.Direction =
        when (this) {
            Direction.OUT -> com.kakao.actionbase.v2.core.metadata.Direction.OUT
            Direction.IN -> com.kakao.actionbase.v2.core.metadata.Direction.IN
        }

    /**
     * Returns a new [DataFrame] whose base edge columns are renamed from v2 internal
     * names to v3 public names. Rows are shared (not copied). Columns not present in
     * [V2_TO_V3_BASE_COLUMN_NAMES] (including user-defined property fields) pass through
     * unchanged.
     */
    private fun toV3(df: DataFrame): DataFrame {
        val newFields =
            df.schema.fields
                .map { field ->
                    val v3Name = V2_TO_V3_BASE_COLUMN_NAMES[field.name] ?: return@map field
                    Field(v3Name, field.type, field.isNullable, field.desc)
                }.toTypedArray()
        return DataFrame(df.rows, schema = StructType(newFields))
    }

    companion object {
        /**
         * Mapping from v2 internal edge column names ([EdgeSchema.Fields]) to v3 public
         * edge column names. VERSION/SOURCE/TARGET come from the shared neutral
         * [SystemProperties] enum; DIRECTION is a local literal since it is not
         * defined there.
         */
        private val V2_TO_V3_BASE_COLUMN_NAMES: Map<String, String> =
            mapOf(
                EdgeSchema.Fields.TS to SystemProperties.VERSION.getStr(), // "ts"  -> "version"
                EdgeSchema.Fields.SRC to SystemProperties.SOURCE.getStr(), // "src" -> "source"
                EdgeSchema.Fields.TGT to SystemProperties.TARGET.getStr(), // "tgt" -> "target"
                EdgeSchema.Fields.DIR to "direction", // "dir" -> "direction"
            )
    }
}
