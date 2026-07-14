package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val REFRESH_TABLE_DATABASE = "topk"
    const val REFRESH_TABLE_NAME = "refresh"

    const val GLOBAL_ENTITY = "__global__"

    // 2 x 3 x 5 x 7 x 11
    private const val REFRESH_PARTITION_COUNT = 2310

    fun scoreSourceKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): String = "$database.$table:$topk:${direction.name}:$entity"

    // refreshAt is embedded in the key so two events for the same coordinates never collide
    // even when their refresh times differ.
    fun refreshTargetKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        target: String,
        refreshAt: Long,
    ): String = "$database.$table:$topk:${direction.name}:$entity:$target:$refreshAt"

    fun refreshPartition(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        target: String,
    ): Long =
        Math.floorMod(
            "$database.$table:$topk:${direction.name}:$entity:$target".hashCode(),
            REFRESH_PARTITION_COUNT,
        ).toLong()
}
