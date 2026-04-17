package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Group
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
    val schema: ModelSchema? = null,
    val mode: MutationMode? = null,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String? = null,
) {
    fun toV2EdgeSchema(): EdgeSchema? =
        schema?.let {
            when (it) {
                is ModelSchema.Edge ->
                    EdgeSchema(
                        VertexField(it.source.type.toV2VertexType(), it.source.comment),
                        VertexField(it.target.type.toV2VertexType(), it.target.comment),
                        it.properties.map { prop ->
                            V2Field(prop.name, prop.type.toV2DataType(), prop.nullable, prop.comment)
                        },
                    )
                is ModelSchema.MultiEdge -> {
                    val idField = V2Field("_id", it.id.type.toV2DataType(), false, it.id.comment)
                    EdgeSchema(
                        VertexField(it.source.type.toV2VertexType(), it.source.comment),
                        VertexField(it.target.type.toV2VertexType(), it.target.comment),
                        listOf(idField) +
                            it.properties.map { prop ->
                                V2Field(prop.name, prop.type.toV2DataType(), prop.nullable, prop.comment)
                            },
                    )
                }
            }
        }

    fun toV2Indices(): List<V2Index>? =
        schema?.let {
            when (it) {
                is ModelSchema.Edge -> it.indexes.map { idx -> idx.toV2Index() }
                is ModelSchema.MultiEdge -> it.indexes.map { idx -> idx.toV2Index() }
            }
        }

    fun toV2Groups(): List<Group>? =
        schema?.let {
            when (it) {
                is ModelSchema.Edge -> it.groups
                is ModelSchema.MultiEdge -> it.groups
            }
        }

    fun toV2Caches(): List<Cache>? =
        schema?.let {
            when (it) {
                is ModelSchema.Edge -> it.caches
                is ModelSchema.MultiEdge -> it.caches
            }
        }
}
