package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val EXPIRE_TABLE_DATABASE = "topk"
    const val EXPIRE_TABLE_NAME = "expire"

    const val GLOBAL_ENTITY = "__global__"

    // score table source key: {database}.{table}:{topk}:{direction}:{entity}
    fun scoreSourceKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
    ): String = "$database.$table:$topk:${direction.name}:$entity"
}
