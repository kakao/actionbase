package com.kakao.actionbase.core.edge.payload

data class EdgeMutationResponse(
    val results: List<Item>,
) {
    data class Item(
        val source: Any,
        val target: Any,
        val status: String,
        val count: Int,
    )

    companion object {
        fun from(statuses: List<EdgeMutationStatus>) =
            EdgeMutationResponse(
                statuses
                    .map { Item(source = it.source, target = it.target, count = it.count, status = it.status) }
                    .sortedBy { "${it.source}:${it.target}" },
            )
    }
}
