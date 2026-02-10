package com.kakao.actionbase.core.edge.payload

data class MultiEdgeMutationResponse(
    val results: List<Item>,
) {
    data class Item(
        val id: Any,
        val status: String,
        val count: Int,
    )

    companion object {
        fun from(statuses: List<MultiEdgeMutationStatus>) =
            MultiEdgeMutationResponse(
                statuses
                    .map { Item(id = it.id, count = it.count, status = it.status) }
                    .sortedBy { it.toString() },
            )
    }
}
