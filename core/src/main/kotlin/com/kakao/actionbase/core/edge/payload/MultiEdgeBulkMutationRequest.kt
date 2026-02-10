package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.edge.MultiEdge
import com.kakao.actionbase.core.edge.MultiEdgeEvent
import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType

data class MultiEdgeBulkMutationRequest(
    val mutations: List<MutationItem>,
) {
    data class MutationItem(
        val type: EventType,
        val edge: MultiEdge,
    ) : MutationEvent.Source<MultiEdgeEvent> {
        override fun createEvent(schema: ModelSchema): MultiEdgeEvent {
            val multiEdgeSchema = schema as ModelSchema.MultiEdge
            val id = multiEdgeSchema.id.type.cast(edge.id)
            val additionalProperties =
                listOfNotNull(
                    edge.source?.let { "_source" to multiEdgeSchema.source.type.cast(it) },
                    edge.target?.let { "_target" to multiEdgeSchema.target.type.cast(it) },
                )
            val event =
                Event.create(
                    type = type,
                    version = edge.version,
                    properties =
                        multiEdgeSchema.properties
                            .mapNotNull { field ->
                                edge.properties[field.name]?.let { value ->
                                    field.name to field.type.cast(value)
                                }
                            }.toMap() + additionalProperties,
                )

            return MultiEdgeEvent(id, event)
        }
    }
}
