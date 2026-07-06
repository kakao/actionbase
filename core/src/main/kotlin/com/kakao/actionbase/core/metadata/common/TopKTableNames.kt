package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSourceKey(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"
}
