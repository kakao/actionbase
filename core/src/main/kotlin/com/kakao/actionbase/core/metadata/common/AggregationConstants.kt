package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_REFRESH_TABLE = "refresh"

    const val TOPK_REFRESH_PARTITIONS = 2310

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSource(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"

    fun refreshSource(
        table: String,
        topk: String,
        entity: String,
        target: String,
    ): String =
        XXHash32Wrapper.default
            .stringHash("$table|$topk|$entity|$target")
            .mod(TOPK_REFRESH_PARTITIONS)
            .toString()

    fun refreshTarget(
        table: String,
        topk: String,
        entity: String,
        target: String,
        refreshAt: Long,
    ): String = "$table|$topk|$entity|$target|$refreshAt"
}
