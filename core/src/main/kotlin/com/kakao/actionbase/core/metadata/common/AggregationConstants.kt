package com.kakao.actionbase.core.metadata.common

object AggregationConstants {
    object Topk {
        const val DATABASE = "topk"
        const val REFRESH_TABLE = "refresh"

        // entity sentinel for global (non per-entity) rankings
        const val GLOBAL_ENTITY = "__GLOBAL__"

        // rank table index: rows sorted by `metric` descending, i.e. top-K read order
        const val RANK_INDEX = "metric_desc"

        // rank table src key: database | table | topk | entity | dimensionValue1 | dimensionValue2 | ...
        fun rankSource(
            database: String,
            table: String,
            topk: String,
            entity: String,
            dimensionValues: List<String>,
        ): String = (listOf(database, table, topk, entity) + dimensionValues).joinToString("|")

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
