package com.kakao.actionbase.core.vertex.payload

import com.fasterxml.jackson.annotation.JsonInclude
import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.MutationResult

data class VertexMutationResponse(
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
            VertexMutationResponse(
                results
                    .map {
                        // vertex uses the edge mutation with source=id, target=VERTEX_MARKER.
                        val key =
                            it.key as? MutationKey.SourceTarget
                                ?: error("VertexMutationResponse requires SourceTarget key, got ${it.key::class.simpleName}")
                        require(key.target == Constants.VERTEX_MARKER) {
                            "VertexMutationResponse requires target=${Constants.VERTEX_MARKER}, got ${key.target}"
                        }
                        Item(
                            id = key.source,
                            count = it.count,
                            status = it.status,
                            context = it.context,
                        )
                    }.sortedBy { it.id.toString() },
            )
    }
}
