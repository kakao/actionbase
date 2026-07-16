package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_REFRESH_TABLE = "refresh"

    const val GLOBAL_ENTITY = "__global__"
    const val ALL_SEGMENT = "__all__"

    // Field references usable in Topk.entity/Topk.rankedField: the raw edge endpoints, or any
    // property name. The endpoint refs win over a property that happens to share the name.
    const val SOURCE_FIELD = "source"
    const val TARGET_FIELD = "target"

    const val TOPK_REFRESH_PARTITIONS = 2310

    // Distinct from the ranges syntax (':' ';' ','); entity/rankedField values must not contain it.
    const val KEY_DELIMITER = '|'

    // score table src key = {database}.{table}|{topk}|{direction}|{entity}|{segment} — supports
    // multiple topk in one table. The segment block is always present: a segment is stored raw,
    // unencoded; a topk without a segment fills the block with the __all__ sentinel (the score
    // ranks across all events).
    fun scoreSource(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String?,
    ): String = "$database.$table|$topk|${direction.name}|$entity|${segmentBlock(segment)}"

    fun refreshSource(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String?,
        rankedField: String,
    ): Long =
        XXHash32Wrapper.default
            .stringHash("${scoreSource(database, table, topk, direction, entity, segment)}|$rankedField")
            .mod(TOPK_REFRESH_PARTITIONS)
            .toLong()

    // refreshAt is embedded in the key so two events for the same coordinates never collide
    // even when their refresh times differ.
    fun refreshTarget(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String?,
        rankedField: String,
        refreshAt: Long,
    ): String = "${scoreSource(database, table, topk, direction, entity, segment)}|$rankedField|$refreshAt"

    // Inverse of refreshTarget. The segment block is the only elastic component (it may itself
    // contain the delimiter), so the key is parsed 4 blocks from the left and 2 from the right —
    // which is also why entity and rankedField values must not contain the delimiter.
    fun parseRefreshTarget(key: String): RefreshTargetKey? {
        val head = key.split(KEY_DELIMITER, limit = 4)
        if (head.size < 4) return null
        val fqn = head[0]
        val dot = fqn.indexOf('.')
        if (dot <= 0 || dot >= fqn.lastIndex) return null
        val direction = runCatching { Direction.valueOf(head[2]) }.getOrNull() ?: return null

        // rest = {entity}|{segment}|{rankedField}|{refreshAt}
        val rest = head[3]
        val entityEnd = rest.indexOf(KEY_DELIMITER)
        if (entityEnd <= 0) return null
        val refreshAtStart = rest.lastIndexOf(KEY_DELIMITER)
        val rankedFieldStart = rest.lastIndexOf(KEY_DELIMITER, refreshAtStart - 1)
        if (rankedFieldStart <= entityEnd) return null
        val refreshAt = rest.substring(refreshAtStart + 1).toLongOrNull() ?: return null

        return RefreshTargetKey(
            database = fqn.substring(0, dot),
            table = fqn.substring(dot + 1),
            topk = head[1],
            direction = direction,
            entity = rest.substring(0, entityEnd),
            segment = rest.substring(entityEnd + 1, rankedFieldStart).takeIf { it != ALL_SEGMENT },
            rankedField = rest.substring(rankedFieldStart + 1, refreshAtStart),
            refreshAt = refreshAt,
        )
    }

    data class RefreshTargetKey(
        val database: String,
        val table: String,
        val topk: String,
        val direction: Direction,
        val entity: String,
        val segment: String?,
        val rankedField: String,
        val refreshAt: Long,
    )

    private fun segmentBlock(segment: String?): String = segment?.takeIf { it.isNotBlank() } ?: ALL_SEGMENT
}
