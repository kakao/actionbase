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
    @field:NotNull(message = "schema is required")
    @field:Valid
    val schema: ModelSchema.Edge,
    @field:NotBlank(message = "storage is required")
    @field:Pattern(
        regexp = "^datastore://[a-z]+/[a-zA-Z0-9_-]+$",
        message = "storage must be in format datastore://<type>/<name> (e.g., datastore://hbase/my-table)",
    )
    val storage: String,
    val mode: MutationMode = MutationMode.SYNC,
    val type: LabelType = LabelType.HASH,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String = "",
) {
    fun toV2EdgeSchema(): EdgeSchema =
        EdgeSchema(
            VertexField(schema.source.type.toV2VertexType(), schema.source.comment),
            VertexField(schema.target.type.toV2VertexType(), schema.target.comment),
            schema.properties.map {
                V2Field(it.name, it.type.toV2DataType(), it.nullable, it.comment)
            },
        )

    fun toV2DirectionType(): V2DirectionType = schema.direction.toV2DirectionType()

    fun toV2Indices(): List<V2Index> = schema.indexes.map { it.toV2Index() }
}
