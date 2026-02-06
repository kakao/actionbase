package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DataType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2Index
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2VertexType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class TableCreateRequest(
    @field:NotBlank(message = "table is required")
    @field:Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,63}$",
        message = "table must start with a letter, contain only alphanumeric/underscore/hyphen, max 64 chars",
    )
    val table: String,
    @field:NotNull(message = "schema is required")
    @field:Valid
    val schema: ModelSchema,
    @field:NotBlank(message = "storage is required")
    @field:Pattern(
        regexp = "^datastore://[a-z_]+/[a-zA-Z0-9_]+$",
        message = "storage must be in format datastore://<namespace>/<table> (e.g., datastore://my_namespace/my_table)",
    )
    val storage: String,
    val mode: MutationMode = MutationMode.SYNC,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String,
) {
    fun toV2EdgeSchema(): EdgeSchema =
        when (schema) {
            is ModelSchema.Edge ->
                EdgeSchema(
                    VertexField(schema.source.type.toV2VertexType(), schema.source.comment),
                    VertexField(schema.target.type.toV2VertexType(), schema.target.comment),
                    schema.properties.map {
                        V2Field(it.name, it.type.toV2DataType(), it.nullable, it.comment)
                    },
                )
            is ModelSchema.MultiEdge -> {
                val idField = V2Field("_id", schema.id.type.toV2DataType(), false, schema.id.comment)
                EdgeSchema(
                    VertexField(schema.source.type.toV2VertexType(), schema.source.comment),
                    VertexField(schema.target.type.toV2VertexType(), schema.target.comment),
                    listOf(idField) +
                        schema.properties.map {
                            V2Field(it.name, it.type.toV2DataType(), it.nullable, it.comment)
                        },
                )
            }
        }

    fun toV2DirectionType(): V2DirectionType =
        when (schema) {
            is ModelSchema.Edge -> schema.direction.toV2DirectionType()
            is ModelSchema.MultiEdge -> schema.direction.toV2DirectionType()
        }

    fun toV2Indices(): List<V2Index> =
        when (schema) {
            is ModelSchema.Edge -> schema.indexes.map { it.toV2Index() }
            is ModelSchema.MultiEdge -> schema.indexes.map { it.toV2Index() }
        }

    fun labelType(): LabelType =
        when (schema) {
            is ModelSchema.Edge -> LabelType.INDEXED
            is ModelSchema.MultiEdge -> LabelType.MULTI_EDGE
        }
}
