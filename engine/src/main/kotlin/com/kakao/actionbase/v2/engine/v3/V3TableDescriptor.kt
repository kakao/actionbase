package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.EdgeField
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.LabelEntity

sealed class V3TableDescriptor {
    abstract val database: String
    abstract val table: String
    abstract val schema: ModelSchema

    data class Edge(
        override val database: String,
        override val table: String,
        override val schema: ModelSchema.Edge,
    ) : V3TableDescriptor()

    data class MultiEdge(
        override val database: String,
        override val table: String,
        override val schema: ModelSchema.MultiEdge,
    ) : V3TableDescriptor()

    companion object {
        fun create(entity: LabelEntity): V3TableDescriptor {
            val database = entity.name.service
            val table = entity.name.nameNotNull

            val isMultiEdgeTable = entity.type == LabelType.MULTI_EDGE
            return if (isMultiEdgeTable) {
                val schema =
                    ModelSchema.MultiEdge(
                        id = entity.schema.getId(),
                        source =
                            entity.schema.src.toV3(),
                        target =
                            entity.schema.tgt.toV3(),
                        properties =
                            entity.schema.fields
                                .filterNot { it.name == "_id" }
                                .map { it.toV3() },
                        direction = entity.dirType.toV3(),
                        groups = entity.groups.map { it.toV3() },
                        indexes = entity.indices.map { it.toV3() },
                        caches = entity.caches.map { it.toV3() },
                    )
                MultiEdge(
                    database = database,
                    table = table,
                    schema = schema,
                )
            } else {
                val schema =
                    ModelSchema.Edge(
                        source =
                            entity.schema.src.toV3(),
                        target =
                            entity.schema.tgt.toV3(),
                        properties =
                            entity.schema.fields.map { it.toV3() },
                        direction = entity.dirType.toV3(),
                        groups = entity.groups.map { it.toV3() },
                        indexes = entity.indices.map { it.toV3() },
                        caches = entity.caches.map { it.toV3() },
                    )
                Edge(
                    database = database,
                    table = table,
                    schema = schema,
                )
            }
        }

        private fun EdgeSchema.getId(): Field {
            val idField = this.fields.find { it.name == "_id" }!!
            return Field(
                type = idField.type.toV3Type(),
                comment = idField.desc,
            )
        }

        private fun VertexField.toV3(): Field =
            Field(
                type = this.type.toV3Type(),
                comment = this.desc,
            )

        private fun com.kakao.actionbase.v2.core.types.Field.toV3(): StructField =
            StructField(
                name = name,
                type = type.toV3Type(),
                nullable = isNullable,
                comment = desc,
            )

        private fun Order.toV3(): com.kakao.actionbase.core.java.codec.common.hbase.Order =
            when (this) {
                Order.ASC -> com.kakao.actionbase.core.java.codec.common.hbase.Order.ASC
                Order.DESC -> com.kakao.actionbase.core.java.codec.common.hbase.Order.DESC
            }

        private fun VertexType.toV3Type(): PrimitiveType =
            when (this) {
                VertexType.STRING -> PrimitiveType.STRING
                VertexType.LONG -> PrimitiveType.LONG
            }

        private fun DataType.toV3Type(): PrimitiveType =
            when (this) {
                DataType.BYTE -> PrimitiveType.BYTE
                DataType.SHORT -> PrimitiveType.SHORT
                DataType.INT -> PrimitiveType.INT
                DataType.LONG -> PrimitiveType.LONG
                DataType.BOOLEAN -> PrimitiveType.BOOLEAN
                DataType.FLOAT -> PrimitiveType.FLOAT
                DataType.DOUBLE -> PrimitiveType.DOUBLE
                DataType.STRING -> PrimitiveType.STRING
                DataType.JSON -> PrimitiveType.OBJECT
                else -> throw IllegalArgumentException("Unsupported DataType: $this")
            }

        private fun DirectionType.toV3(): com.kakao.actionbase.core.metadata.common.DirectionType =
            when (this) {
                DirectionType.IN -> com.kakao.actionbase.core.metadata.common.DirectionType.IN
                DirectionType.OUT -> com.kakao.actionbase.core.metadata.common.DirectionType.OUT
                DirectionType.BOTH -> com.kakao.actionbase.core.metadata.common.DirectionType.BOTH
            }

        private fun com.kakao.actionbase.v2.core.code.Index.toV3(): Index =
            Index(
                index = this.name,
                fields =
                    this.fields.map { field ->
                        IndexField(EdgeField.toV3(field.name), field.order.toV3())
                    },
                comment = this.desc,
            )

        private fun Group.toV3(): Group = copy(fields = fields.map { it.toV3() })

        private fun Group.Field.toV3(): Group.Field = copy(name = EdgeField.toV3(name))

        private fun Cache.toV3(): Cache = copy(fields = fields.map { it.copy(field = EdgeField.toV3(it.field)) })
    }
}
