package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.state.AbstractSchema
import com.kakao.actionbase.core.state.Schema

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ModelSchema.Edge::class, name = "EDGE"),
    JsonSubTypes.Type(value = ModelSchema.MultiEdge::class, name = "MULTI_EDGE"),
)
sealed class ModelSchema : AbstractSchema {
    abstract val properties: List<StructField>

    @JsonTypeName("edge")
    data class Edge(
        val source: Field,
        val target: Field,
        override val properties: List<StructField> = emptyList(),
        val direction: DirectionType,
        val indexes: List<Index> = emptyList(),
        val groups: List<Group> = emptyList(),
        val caches: List<Cache> = emptyList(),
    ) : ModelSchema(),
        AbstractSchema by Schema(properties.associate { it.name to it.nullable })

    @JsonTypeName("multiEdge")
    data class MultiEdge(
        val id: Field,
        val source: Field, // source is stored in properties
        val target: Field, // target is stored in properties
        override val properties: List<StructField> = emptyList(),
        val direction: DirectionType,
        val indexes: List<Index> = emptyList(),
        val groups: List<Group> = emptyList(),
        val caches: List<Cache> = emptyList(),
    ) : ModelSchema(),
        AbstractSchema by Schema(properties.associate { it.name to it.nullable } + listOf("_source" to false, "_target" to false))
}
