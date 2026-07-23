package com.kakao.actionbase.core.metadata.common

object AggregationConstants {
    object Topk {
        const val DATABASE = "topk"
        const val REFRESH_TABLE = "refresh"

        // partition count for the refresh queue, set at queue creation; the queue hashes the message key into it
        const val REFRESH_PARTITIONS = 2310

        // entity sentinel for global (non per-entity) rankings
        const val GLOBAL_ENTITY = "__GLOBAL__"

        // rank table src key: topk | entity | dimensionValue1 | dimensionValue2 | ...
        fun rankSource(
            topk: String,
            entity: String,
            dimensionValues: List<String>,
        ): String = (listOf(topk, entity) + dimensionValues).joinToString("|")

        // refresh queue message key: database | table | topk | entity | topkDimensionValue | dimensionValue1 | ...
        // the queue derives the partition from this key, so pass the raw composite (not a pre-hashed value).
        fun refreshKey(
            database: String,
            table: String,
            topk: String,
            entity: String,
            topkDimensionValue: String,
            dimensionValues: List<String>,
        ): String = (listOf(database, table, topk, entity, topkDimensionValue) + dimensionValues).joinToString("|")
    }
}
