package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.state.AbstractSchema
import com.kakao.actionbase.core.state.Schema

import com.fasterxml.jackson.annotation.JsonIgnore
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
    JsonSubTypes.Type(value = ModelSchema.Vertex::class, name = "VERTEX"),
    JsonSubTypes.Type(value = ModelSchema.ImmutableEdge::class, name = "IMMUTABLE_EDGE"),
)
sealed class ModelSchema : AbstractSchema {
    abstract val properties: List<StructField>

    @get:JsonIgnore
    val propertiesByName: Map<String, StructField> by lazy { properties.associateBy { it.name } }

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

    @JsonTypeName("vertex")
    data class Vertex(
        val id: Field,
        override val properties: List<StructField> = emptyList(),
    ) : ModelSchema(),
        AbstractSchema by Schema(properties.associate { it.name to it.nullable })

    /**
     * Index-only edge with no `State` row: append-oriented, read-via-scan, delete-via-scan.
     * Structurally an [Edge] minus caches — indexes drive scan, groups back count/sum topK.
     * No count records (a scalar per source does not survive append semantics).
     */
    @JsonTypeName("immutableEdge")
    data class ImmutableEdge(
        val source: Field,
        val target: Field,
        override val properties: List<StructField> = emptyList(),
        val direction: DirectionType,
        val indexes: List<Index> = emptyList(),
        val groups: List<Group> = emptyList(),
    ) : ModelSchema(),
        AbstractSchema by Schema(properties.associate { it.name to it.nullable })
}
