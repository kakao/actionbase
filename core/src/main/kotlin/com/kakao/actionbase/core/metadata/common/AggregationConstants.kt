package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_EXPIRE_TABLE = "expire"

    const val TOPK_EXPIRE_PARTITIONS = 2310

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSource(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"

    fun expireSource(
        table: String,
        topk: String,
        entity: String,
        target: String,
    ): String =
        XXHash32Wrapper.default
            .stringHash("$table|$topk|$entity|$target")
            .mod(TOPK_EXPIRE_PARTITIONS)
            .toString()

    fun expireTarget(
        table: String,
        topk: String,
        entity: String,
        target: String,
        expiresAt: Long,
    ): String = "$table|$topk|$entity|$target|$expiresAt"
}
