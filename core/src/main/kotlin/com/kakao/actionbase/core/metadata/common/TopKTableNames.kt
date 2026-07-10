package com.kakao.actionbase.core.metadata.common

object TopKTableNames {
    const val EXPIRE_TABLE_DATABASE = "topk"
    const val EXPIRE_TABLE_NAME = "expire"

    // GLOBAL scope topk의 고정 entity — 모든 score row가 같은 rowkey prefix를 공유해 prefix scan 한 번으로 top-K를 뽑을 수 있게 한다.
    const val GLOBAL_ENTITY = "__global__"

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSourceKey(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"
}
