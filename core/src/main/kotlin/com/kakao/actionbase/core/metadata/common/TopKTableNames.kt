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

    // expire row target: a stable single key per (database, table, topk, direction, entity),
    // so replaying the same coordinates upserts the existing expire row instead of adding a new one.
    fun expireTargetKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): String = scoreSourceKey(database, table, topk, direction, entity)

    // expire table partition, hashed from the same coordinates as the score row it protects.
    fun expirePartition(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): Long =
        Math.floorMod(
            "$database.$table:$topk:${direction.name}:$entity".hashCode(),
            EXPIRE_PARTITION_COUNT,
        ).toLong()
}
