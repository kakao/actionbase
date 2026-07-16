package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_REFRESH_TABLE = "refresh"

    const val GLOBAL_ENTITY = "__global__"
    const val ALL_SEGMENT = "__all__"

    const val TOPK_REFRESH_PARTITIONS = 2310

    // score table src key = {database}.{table}:{topk}:{direction}:{entity}:{segment} — supports
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
    ): String = "$database.$table:$topk:${direction.name}:$entity:${segmentBlock(segment)}"

    fun refreshSource(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String?,
        target: String,
    ): Long =
        XXHash32Wrapper.default
            .stringHash("${scoreSource(database, table, topk, direction, entity, segment)}:$target")
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
        target: String,
        refreshAt: Long,
    ): String = "${scoreSource(database, table, topk, direction, entity, segment)}:$target:$refreshAt"

    private fun segmentBlock(segment: String?): String = segment?.takeIf { it.isNotBlank() } ?: ALL_SEGMENT
}
