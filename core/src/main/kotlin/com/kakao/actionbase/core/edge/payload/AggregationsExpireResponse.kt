package com.kakao.actionbase.core.edge.payload

data class AggregationsExpireResponse(
    val items: List<Item>,
) {
    data class Item(
        val database: String,
        val table: String,
        val source: String,
        val status: String,
        val error: String?,
    )

    companion object {
        fun from(expireResults: List<AggregationExpireResult>): AggregationsExpireResponse =
            AggregationsExpireResponse(
                items =
                    expireResults.map { aggregationResult ->
                        Item(
                            database = aggregationResult.database,
                            table = aggregationResult.table,
                            source = aggregationResult.source,
                            status = aggregationResult.status,
                            error = aggregationResult.error,
                        )
                    },
            )
    }
}
