package com.kakao.actionbase.core.vertex.payload

import com.kakao.actionbase.core.Constants.VERTEX_MARKER
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.edge.UnresolvedEvent
import com.kakao.actionbase.core.edge.payload.checkNonNullableFields
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.vertex.Vertex

data class VertexBulkMutationRequest(
    val mutations: List<MutationItem>,
) {
    data class MutationItem(
        val type: EventType,
        val vertex: Vertex,
    ) : UnresolvedEvent {
        override fun createEvent(
            schema: ModelSchema,
            insertMerge: Boolean,
        ): MutationEvent {
            require(schema is ModelSchema.Vertex) { "Expected ModelSchema.Vertex, but got ${schema::class.simpleName}" }
            val id = schema.id.type.cast(vertex.id)
            require(id.toString() != VERTEX_MARKER) { "Vertex id cannot be '$VERTEX_MARKER' (reserved marker)" }
            checkNonNullableFields(type, schema.properties, vertex.properties, insertMerge)
            val event =
                Event.create(
                    type = type,
                    version = vertex.version,
                    properties =
                        schema.properties
                            .filter { field -> field.name in vertex.properties.keys }
                            .associate { field ->
                                val value = vertex.properties[field.name]
                                field.name to if (value != null) field.type.cast(value) else null
                            },
                )
            return EdgeEvent(id, VERTEX_MARKER, event)
        }
    }
}
