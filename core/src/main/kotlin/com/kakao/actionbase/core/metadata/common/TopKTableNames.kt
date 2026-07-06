package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    // score: same database as original table, suffix __topk (shared across all topk names)
    fun scoreTableName(originalTable: String): String = "${originalTable}__topk"

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSourceKey(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"
}
