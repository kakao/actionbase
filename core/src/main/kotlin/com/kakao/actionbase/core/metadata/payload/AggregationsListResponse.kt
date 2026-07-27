package com.kakao.actionbase.core.metadata.payload

import com.kakao.actionbase.core.metadata.Dedupe
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType

data class AggregationsListResponse(
    val items: List<Item>,
) {
    data class Item(
        val type: AggregationType,
        val database: String,
        val table: String,
        val dedupes: List<Dedupe>,
    )

    companion object {
        fun of(aggregations: List<QualifiedAggregations>): AggregationsListResponse =
            AggregationsListResponse(
                items =
                    aggregations.map {
                        Item(type = it.type, database = it.database, table = it.table, dedupes = it.dedupes)
                    },
            )
    }
}
