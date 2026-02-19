package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType

data class EdgeBulkMutationRequest(
    val mutations: List<MutationItem>,
) {
    data class MutationItem(
        val type: EventType,
        val edge: Edge,
    ) : MutationEvent.Source<EdgeEvent> {
        override fun createEvent(schema: ModelSchema): EdgeEvent {
            require(schema is ModelSchema.Edge) { "Expected ModelSchema.Edge, but got ${schema::class.simpleName}" }
            val source = schema.source.type.cast(edge.source)
            val target = schema.target.type.cast(edge.target)
            val event =
                Event.create(
                    type = type,
                    version = edge.version,
                    properties =
                        schema.properties
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
