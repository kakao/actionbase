package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.MutationKey

import com.fasterxml.jackson.annotation.JsonInclude

data class EdgeMutationResponse(
    val results: List<Item>,
) {
    data class Item(
        val source: Any,
        val target: Any,
        val status: String,
        val count: Int,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val context: Map<String, Any?>? = null,
    )

    companion object {
        fun from(results: List<MutationResult>) =
            EdgeMutationResponse(
                results
                    .map {
                        val key =
                            it.key as? MutationKey.SourceTarget
                                ?: error("EdgeMutationResponse requires SourceTarget key, got ${it.key::class.simpleName}")
                        Item(
                            source = key.source,
                            target = key.target,
                            count = it.count,
                            status = it.status,
                            context = it.context,
                        )
                    }.sortedBy { "${it.source}:${it.target}" },
            )
    }
}
