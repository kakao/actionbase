package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val EXPIRE_TABLE_DATABASE = "topk"
    const val EXPIRE_TABLE_NAME = "expire"

    const val GLOBAL_ENTITY = "__global__"

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSourceKey(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"
}
