package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.MutationKey

import com.fasterxml.jackson.annotation.JsonInclude

data class MultiEdgeMutationResponse(
    val results: List<Item>,
) {
    data class Item(
        val id: Any,
        val status: String,
        val count: Int,
        @field:JsonInclude(JsonInclude.Include.NON_NULL)
        val context: Map<String, Any?>? = null,
    )

    companion object {
        fun from(results: List<MutationResult>) =
            MultiEdgeMutationResponse(
                results
                    .map {
                        val key =
                            it.key as? MutationKey.Id
                                ?: error("MultiEdgeMutationResponse requires Id key, got ${it.key::class.simpleName}")
                        Item(id = key.id, count = it.count, status = it.status, context = it.context)
                    }.sortedBy { it.id.toString() },
            )
    }
}
