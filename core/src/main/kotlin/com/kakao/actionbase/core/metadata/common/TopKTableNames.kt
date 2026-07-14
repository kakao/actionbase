package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val EXPIRE_TABLE_DATABASE = "topk"
    const val EXPIRE_TABLE_NAME = "expire"

    const val GLOBAL_ENTITY = "__global__"

    // Number of expire-table partitions, matching the per-entity-top-k design doc (2 x 3 x 5 x 7 x 11).
    private const val EXPIRE_PARTITION_COUNT = 2310

    // score table source key: {database}.{table}:{topk}:{direction}:{entity}
    fun scoreSourceKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): String = "$database.$table:$topk:${direction.name}:$entity"

    // expire row target: one row per event, so a sliding window can independently track when
    // each contributing event falls out of range. expiredAt is embedded in the key itself: two
    // events for the same (database, table, topk, direction, entity, target) never collide even
    // if their expiry times differ.
    fun expireTargetKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        target: String,
        expiredAt: Long,
    ): String = "$database.$table:$topk:${direction.name}:$entity:$target:$expiredAt"

    // expire table partition, hashed from the full event coordinates (excluding expiredAt) so
    // events spread across the fixed partition space instead of collapsing onto one entity.
    fun expirePartition(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        target: String,
    ): Long =
        Math.floorMod(
            "$database.$table:$topk:${direction.name}:$entity:$target".hashCode(),
            EXPIRE_PARTITION_COUNT,
        ).toLong()
}
