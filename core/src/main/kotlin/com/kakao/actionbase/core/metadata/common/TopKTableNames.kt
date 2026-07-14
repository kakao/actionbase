package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val EXPIRE_TABLE_DATABASE = "topk"
    const val EXPIRE_TABLE_NAME = "expire"

    const val GLOBAL_ENTITY = "__global__"

    // 2 x 3 x 5 x 7 x 11
    private const val EXPIRE_PARTITION_COUNT = 2310

    fun scoreSourceKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): String = "$database.$table:$topk:${direction.name}:$entity"

    // expiredAt is embedded in the key so two events for the same coordinates never collide
    // even when their expiry times differ.
    fun expireTargetKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        target: String,
        expiredAt: Long,
    ): String = "$database.$table:$topk:${direction.name}:$entity:$target:$expiredAt"

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
