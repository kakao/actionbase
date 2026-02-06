package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode

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
    JsonSubTypes.Type(value = TableDescriptor.Edge::class, name = "edge"),
    JsonSubTypes.Type(value = TableDescriptor.MultiEdge::class, name = "multiEdge"),
)
sealed class TableDescriptor<T : ModelSchema> : V3Descriptor<TableId> {
    abstract val database: String

    abstract val table: String

    abstract val schema: T

    abstract val mode: MutationMode

    abstract val storage: String

    @JsonTypeName("edge")
    data class Edge(
        override val tenant: String,
        override val database: String,
        override val table: String,
        override val schema: ModelSchema.Edge,
        override val mode: MutationMode,
        override val storage: String,
        override val active: Boolean = true,
        override val comment: String = Constants.DEFAULT_COMMENT,
        override val revision: Long = Constants.DEFAULT_REVISION,
        override val createdAt: Long = Constants.DEFAULT_CREATED_AT,
        override val createdBy: String = Constants.DEFAULT_CREATED_BY,
        override val updatedAt: Long = Constants.DEFAULT_UPDATED_AT,
        override val updatedBy: String = Constants.DEFAULT_UPDATED_BY,
    ) : TableDescriptor<ModelSchema.Edge>() {
        @JsonIgnore
        override val id: TableId = TableId(tenant, database, table)
    }

    @JsonTypeName("multiEdge")
    data class MultiEdge(
        override val tenant: String,
        override val database: String,
        override val table: String,
        override val schema: ModelSchema.MultiEdge,
        override val mode: MutationMode,
        override val storage: String,
        override val active: Boolean = true,
        override val comment: String = Constants.DEFAULT_COMMENT,
        override val revision: Long = Constants.DEFAULT_REVISION,
        override val createdAt: Long = Constants.DEFAULT_CREATED_AT,
        override val createdBy: String = Constants.DEFAULT_CREATED_BY,
        override val updatedAt: Long = Constants.DEFAULT_UPDATED_AT,
        override val updatedBy: String = Constants.DEFAULT_UPDATED_BY,
    ) : TableDescriptor<ModelSchema.MultiEdge>() {
        @JsonIgnore
        override val id: TableId = TableId(tenant, database, table)
    }
}
