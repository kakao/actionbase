package com.kakao.actionbase.core.metadata.common

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_EXPIRE_TABLE = "expire"

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSourceKey(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"
}
