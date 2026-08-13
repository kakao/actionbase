package com.kakao.actionbase.core.metadata.common

object AggregationConstants {
    object Topk {
        const val DATABASE = "topk"
        const val REFRESH_QUEUE = "refresh"

        // entity sentinel for global (non per-entity) rankings
        const val GLOBAL_ENTITY = "__GLOBAL__"

        // rank table index: rows sorted by `metric` descending, i.e. top-K read order
        const val RANK_INDEX = "metric_desc"

        // rank row properties: the aggregated metric, plus the carried properties as one JSON string
        const val METRIC = "metric"
        const val ADDITIONAL_PROPERTIES = "additionalProperties"

        // rank table src key: database | table | topk | entity | dimensionValue1 | dimensionValue2 | ...
        fun rankSource(
            database: String,
            table: String,
            topk: String,
            entity: String,
            dimensionValues: List<String>,
        ): String = joinValues(listOf(database, table, topk, entity) + dimensionValues)

        // refresh queue message key: database | table | topk | entity | topkDimensionValue | dimensionValue1 | ...
        // the queue derives the partition from this key, so pass the raw composite (not a pre-hashed value).
        fun refreshKey(
            database: String,
            table: String,
            topk: String,
            entity: String,
            topkDimensionValue: String,
            dimensionValues: List<String>,
        ): String = joinValues(listOf(database, table, topk, entity, topkDimensionValue) + dimensionValues)

        /** An entity id or a dimension value can hold the separator itself (`kakao|12345`), which would otherwise let two rankings share one key. */
        fun joinValues(values: List<String>): String = values.joinToString(SEPARATOR.toString()) { escape(it) }

        /** An empty string reads back as no values: a ranking with none joins to the same string as one whose single value is empty, and none is the common case. */
        fun splitValues(joined: String): List<String> {
            if (joined.isEmpty()) return emptyList()

            val values = mutableListOf<String>()
            val value = StringBuilder()
            var i = 0

            while (i < joined.length) {
                val char = joined[i]
                when {
                    char == ESCAPE && i + 1 < joined.length -> value.append(joined[i + 1]).also { i += 2 }
                    char == SEPARATOR -> values.add(value.toString()).also { value.clear() }.also { i++ }
                    else -> value.append(char).also { i++ }
                }
            }

            return values + value.toString()
        }

        private fun escape(value: String): String =
            if (value.none { it == SEPARATOR || it == ESCAPE }) {
                value
            } else {
                buildString {
                    value.forEach { char ->
                        if (char == SEPARATOR || char == ESCAPE) append(ESCAPE)
                        append(char)
                    }
                }
            }

        private const val SEPARATOR = '|'
        private const val ESCAPE = '\\'
    }
}
