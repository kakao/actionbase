package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DataType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2Index
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2VertexType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class TableUpdateRequest(
    val active: Boolean? = null,
    @field:Valid
    val schema: ModelSchema.Edge? = null,
    val mode: MutationMode? = null,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String? = null,
) {
    fun toV2EdgeSchema(): EdgeSchema? =
        schema?.let {
            EdgeSchema(
                VertexField(it.source.type.toV2VertexType(), it.source.comment),
                VertexField(it.target.type.toV2VertexType(), it.target.comment),
                it.properties.map { prop ->
                    V2Field(prop.name, prop.type.toV2DataType(), prop.nullable, prop.comment)
                },
            )
        }

    fun toV2Indices(): List<V2Index>? = schema?.indexes?.map { it.toV2Index() }
}
