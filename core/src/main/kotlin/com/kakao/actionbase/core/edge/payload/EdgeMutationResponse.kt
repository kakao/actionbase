package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.MutationKey

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
        fun from(results: List<MutationResult>) =
            EdgeMutationResponse(
                results
                    .map {
                        val key =
                            it.key as? MutationKey.SourceTarget
                                ?: error("EdgeMutationResponse requires SourceTarget key, got ${it.key::class.simpleName}")
                        Item(source = key.source, target = key.target, count = it.count, status = it.status)
                    }.sortedBy { "${it.source}:${it.target}" },
            )
    }
}
