package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.UnresolvedEvent
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType

data class EdgeBulkMutationRequest(
    val mutations: List<MutationItem>,
) {
    data class MutationItem(
        val type: EventType,
        val edge: Edge,
    ) : UnresolvedEvent {
        override fun createEvent(
            schema: ModelSchema,
            insertMerge: Boolean,
        ): EdgeEvent {
            val (sourceField, targetField, properties) =
                when (schema) {
                    is ModelSchema.Edge -> Triple(schema.source, schema.target, schema.properties)
                    is ModelSchema.ImmutableEdge -> Triple(schema.source, schema.target, schema.properties)
                    else -> throw IllegalArgumentException("Expected ModelSchema.Edge or ImmutableEdge, but got ${schema::class.simpleName}")
                }
            val source = sourceField.type.cast(edge.source)
            val target = targetField.type.cast(edge.target)
            checkNonNullableFields(type, properties, edge.properties, insertMerge)
            val event =
                Event.create(
                    type = type,
                    version = edge.version,
                    properties =
                        properties
                            .filter { field -> field.name in edge.properties.keys }
                            .associate { field ->
                                val value = edge.properties[field.name]
                                field.name to if (value != null) field.type.cast(value) else null
                            },
                )
            return EdgeEvent(source, target, event)
        }
    }
}
