package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_REFRESH_TABLE = "refresh"

    const val TOPK_REFRESH_PARTITIONS = 2310

    // entity sentinel for global (non per-entity) rankings
    const val GLOBAL_ENTITY = "__GLOBAL__"

    // rank table src key: topk | entity | dimensionValue1 | dimensionValue2 | ...
    fun rankSource(
        topk: String,
        entity: String,
        dimensionValues: List<String>,
    ): String = (listOf(topk, entity) + dimensionValues).joinToString("|")

    // refresh table src key: partition of hash(database | table | topk | entity | topkDimensionValue | dimensionValue1 | ...)
    fun refreshSource(
        database: String,
        table: String,
        topk: String,
        entity: String,
        topkDimensionValue: String,
        dimensionValues: List<String>,
    ): String =
        XXHash32Wrapper.default
            .stringHash((listOf(database, table, topk, entity, topkDimensionValue) + dimensionValues).joinToString("|"))
            .mod(TOPK_REFRESH_PARTITIONS)
            .toString()
}
