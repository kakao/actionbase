package com.kakao.actionbase.core.metadata.payload

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType

data class AggregationsListResponse(
    val topk: List<Item>,
) {
    data class Item(
        val database: String,
        val table: String,
        val expire: Boolean = false,
    )

    companion object {
        fun of(aggregations: List<QualifiedAggregations>): AggregationsListResponse =
            AggregationsListResponse(
                topk =
                    aggregations
                        .filter { it.type == AggregationType.TOPK }
                        .map { it.toItem() },
            )

        private fun QualifiedAggregations.toItem(): Item = Item(database = database, table = table, expire = expire)
    }
}
