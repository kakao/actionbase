package com.kakao.actionbase.core.edge.payload

data class AggregationsSweepResponse(
    val items: List<Item>,
) {
    data class Item(
        val database: String,
        val table: String,
        val topk: String,
        val entity: String,
        val status: String,
        val error: String?,
    )

    companion object {
        fun from(sweepResults: List<AggregationSweepResult>): AggregationsSweepResponse =
            AggregationsSweepResponse(
                items =
                    sweepResults.map { result ->
                        Item(
                            database = result.database,
                            table = result.table,
                            topk = result.topk,
                            entity = result.entity,
                            status = result.status,
                            error = result.error,
                        )
                    },
            )
    }
}
