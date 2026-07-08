package com.kakao.actionbase.core.metadata.payload

import com.kakao.actionbase.core.metadata.AggregationMetadata

data class AggregationsListResponse(
    val topk: List<Item>,
) {
    data class Item(
        val database: String,
        val table: String,
    )

    companion object {
        fun of(
            type: AggregationType?,
            metadata: List<AggregationMetadata>,
        ): AggregationsListResponse =
            when (type) {
                AggregationType.TOPK -> AggregationsListResponse(topk = topkItems(metadata))
                else -> AggregationsListResponse(topk = topkItems(metadata))
            }

        private fun topkItems(metadata: List<AggregationMetadata>): List<Item> =
            metadata
                .filter { md -> md.aggregations.any { it.topk.isNotEmpty() } }
                .map { it.toItem() }

        private fun AggregationMetadata.toItem(): Item = Item(database = database, table = table)
    }
}

enum class AggregationType {
    TOPK,
}
