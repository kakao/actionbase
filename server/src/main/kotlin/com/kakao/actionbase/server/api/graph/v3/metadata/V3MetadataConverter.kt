package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order as V3Order
import com.kakao.actionbase.core.metadata.common.DirectionType as V3DirectionType
import com.kakao.actionbase.core.metadata.common.Field as V3Field
import com.kakao.actionbase.core.metadata.common.Index as V3Index
import com.kakao.actionbase.core.metadata.common.MutationMode as V3MutationMode
import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.code.hbase.Order as V2Order
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode
import com.kakao.actionbase.v2.core.types.DataType as V2DataType
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity

object V3MetadataConverter {
    // region Database (V3 DatabaseDescriptor <-> V2 ServiceEntity)

    fun ServiceEntity.toV3DatabaseDescriptor(tenant: String): DatabaseDescriptor =
        DatabaseDescriptor(
            tenant = tenant,
            database = name.nameNotNull,
            active = active,
            comment = desc,
        )

    fun DatabaseDescriptor.toV2ServiceEntity(): ServiceEntity =
        ServiceEntity(
            active = active,
            name = EntityName.fromOrigin(database),
            desc = comment,
        )

    // endregion

    // region Table (V3 TableDescriptor <-> V2 LabelEntity)

    fun LabelEntity.toV3TableDescriptorEdge(tenant: String): TableDescriptor.Edge =
        TableDescriptor.Edge(
            tenant = tenant,
            database = name.service,
            table = name.nameNotNull,
            schema = schema.toV3ModelSchemaEdge(dirType.toV3DirectionType(), indices, groups),
            mode = mode.toV3MutationMode(),
            storage = storage,
            active = active,
            comment = desc,
        )

    fun LabelEntity.toV3TableDescriptorMultiEdge(tenant: String): TableDescriptor.MultiEdge =
        TableDescriptor.MultiEdge(
            tenant = tenant,
            database = name.service,
            table = name.nameNotNull,
            schema = schema.toV3ModelSchemaMultiEdge(dirType.toV3DirectionType(), indices, groups),
            mode = mode.toV3MutationMode(),
            storage = storage,
            active = active,
            comment = desc,
        )

    fun LabelEntity.toV3TableDescriptor(tenant: String): TableDescriptor<*> =
        when (type) {
            LabelType.MULTI_EDGE -> toV3TableDescriptorMultiEdge(tenant)
            else -> toV3TableDescriptorEdge(tenant)
        }

    fun TableDescriptor.Edge.toV2LabelEntity(): LabelEntity =
        LabelEntity(
            active = active,
            name = EntityName(database, table),
            desc = comment,
            type = LabelType.INDEXED,
            schema = schema.toV2EdgeSchema(),
            dirType = schema.direction.toV2DirectionType(),
            storage = storage,
            indices = schema.indexes.map { it.toV2Index() },
            groups = schema.groups,
            event = false,
            readOnly = false,
            mode = mode.toV2MutationMode(),
        )

    fun TableDescriptor.MultiEdge.toV2LabelEntity(): LabelEntity =
        LabelEntity(
            active = active,
            name = EntityName(database, table),
            desc = comment,
            type = LabelType.MULTI_EDGE,
            schema = schema.toV2EdgeSchema(),
            dirType = schema.direction.toV2DirectionType(),
            storage = storage,
            indices = schema.indexes.map { it.toV2Index() },
            groups = schema.groups,
            event = false,
            readOnly = true,
            mode = mode.toV2MutationMode(),
        )

    // endregion

    // region Alias (V3 AliasDescriptor <-> V2 AliasEntity)

    fun AliasEntity.toV3AliasDescriptor(tenant: String): AliasDescriptor =
        AliasDescriptor(
            tenant = tenant,
            database = name.service,
            alias = name.nameNotNull,
            table = target.nameNotNull,
            active = active,
            comment = desc,
        )

    fun AliasDescriptor.toV2AliasEntity(): AliasEntity =
        AliasEntity(
            active = active,
            name = EntityName(database, alias),
            desc = comment,
            target = EntityName(database, table),
        )

    // endregion

    // region MutationMode conversion

    fun V2MutationMode.toV3MutationMode(): V3MutationMode =
        when (this) {
            V2MutationMode.SYNC -> V3MutationMode.SYNC
            V2MutationMode.ASYNC -> V3MutationMode.ASYNC
            V2MutationMode.IGNORE -> V3MutationMode.DROP
        }

    fun V3MutationMode.toV2MutationMode(): V2MutationMode =
        when (this) {
            V3MutationMode.SYNC -> V2MutationMode.SYNC
            V3MutationMode.ASYNC -> V2MutationMode.ASYNC
            V3MutationMode.DROP -> V2MutationMode.IGNORE
            V3MutationMode.DENY -> throw IllegalArgumentException("V3 MutationMode.DENY is not supported in V2")
        }

    // endregion

    // region DirectionType conversion

    fun V2DirectionType.toV3DirectionType(): V3DirectionType =
        when (this) {
            V2DirectionType.BOTH -> V3DirectionType.BOTH
            V2DirectionType.OUT -> V3DirectionType.OUT
            V2DirectionType.IN -> V3DirectionType.IN
        }

    fun V3DirectionType.toV2DirectionType(): V2DirectionType =
        when (this) {
            V3DirectionType.BOTH -> V2DirectionType.BOTH
            V3DirectionType.OUT -> V2DirectionType.OUT
            V3DirectionType.IN -> V2DirectionType.IN
        }

    // endregion

    // region Schema conversion (V3 ModelSchema <-> V2 EdgeSchema)

    fun EdgeSchema.toV3ModelSchemaEdge(
        direction: V3DirectionType,
        indices: List<V2Index>,
        groups: List<Group>,
    ): ModelSchema.Edge =
        ModelSchema.Edge(
            source =
                V3Field(
                    type = src.type.toV3PrimitiveType(),
                    comment = src.desc,
                ),
            target =
                V3Field(
                    type = tgt.type.toV3PrimitiveType(),
                    comment = tgt.desc,
                ),
            properties = fields.map { it.toV3StructField() },
            direction = direction,
            indexes = indices.map { it.toV3Index() },
            groups = groups,
        )

    fun EdgeSchema.toV3ModelSchemaMultiEdge(
        direction: V3DirectionType,
        indices: List<V2Index>,
        groups: List<Group>,
    ): ModelSchema.MultiEdge {
        val idField =
            fields.find { it.name == "_id" }
                ?: throw IllegalArgumentException("MultiEdge schema must have _id field")
        return ModelSchema.MultiEdge(
            id =
                V3Field(
                    type = idField.type.toV3PrimitiveType(),
                    comment = idField.desc,
                ),
            source =
                V3Field(
                    type = src.type.toV3PrimitiveType(),
                    comment = src.desc,
                ),
            target =
                V3Field(
                    type = tgt.type.toV3PrimitiveType(),
                    comment = tgt.desc,
                ),
            properties = fields.filterNot { it.name == "_id" }.map { it.toV3StructField() },
            direction = direction,
            indexes = indices.map { it.toV3Index() },
            groups = groups,
        )
    }

    fun ModelSchema.Edge.toV2EdgeSchema(): EdgeSchema =
        EdgeSchema(
            VertexField(source.type.toV2VertexType(), source.comment),
            VertexField(target.type.toV2VertexType(), target.comment),
            properties.map { it.toV2Field() },
        )

    fun ModelSchema.MultiEdge.toV2EdgeSchema(): EdgeSchema {
        val idField = V2Field("_id", id.type.toV2DataType(), false, id.comment)
        return EdgeSchema(
            VertexField(source.type.toV2VertexType(), source.comment),
            VertexField(target.type.toV2VertexType(), target.comment),
            listOf(idField) + properties.map { it.toV2Field() },
        )
    }

    // endregion

    // region Field conversion

    fun V2Field.toV3StructField(): StructField =
        StructField(
            name = name,
            type = type.toV3PrimitiveType(),
            comment = desc,
            nullable = isNullable,
        )

    fun StructField.toV2Field(): V2Field =
        V2Field(
            name,
            type.toV2DataType(),
            nullable,
            comment,
        )

    // endregion

    // region Index conversion

    fun V2Index.toV3Index(): V3Index =
        V3Index(
            index = name,
            fields = fields.map { IndexField(field = it.name, order = it.order.toV3Order()) },
            comment = desc,
        )

    fun V3Index.toV2Index(): V2Index =
        V2Index(
            index,
            fields.map { V2Index.Field(it.field, it.order.toV2Order()) },
            comment,
        )

    // endregion

    // region Order conversion

    fun V2Order.toV3Order(): V3Order =
        when (this) {
            V2Order.ASC -> V3Order.ASC
            V2Order.DESC -> V3Order.DESC
        }

    fun V3Order.toV2Order(): V2Order =
        when (this) {
            V3Order.ASC -> V2Order.ASC
            V3Order.DESC -> V2Order.DESC
        }

    // endregion

    // region Type conversion

    fun VertexType.toV3PrimitiveType(): PrimitiveType =
        when (this) {
            VertexType.LONG -> PrimitiveType.LONG
            VertexType.STRING -> PrimitiveType.STRING
        }

    fun PrimitiveType.toV2VertexType(): VertexType =
        when (this) {
            PrimitiveType.LONG -> VertexType.LONG
            PrimitiveType.STRING -> VertexType.STRING
            else -> throw IllegalArgumentException("Cannot convert $this to VertexType")
        }

    fun V2DataType.toV3PrimitiveType(): PrimitiveType =
        when (this) {
            V2DataType.BOOLEAN -> PrimitiveType.BOOLEAN
            V2DataType.BYTE -> PrimitiveType.BYTE
            V2DataType.SHORT -> PrimitiveType.SHORT
            V2DataType.INT -> PrimitiveType.INT
            V2DataType.LONG -> PrimitiveType.LONG
            V2DataType.FLOAT -> PrimitiveType.FLOAT
            V2DataType.DOUBLE -> PrimitiveType.DOUBLE
            V2DataType.STRING -> PrimitiveType.STRING
            V2DataType.DECIMAL, V2DataType.JSON -> PrimitiveType.OBJECT
        }

    fun PrimitiveType.toV2DataType(): V2DataType =
        when (this) {
            PrimitiveType.BOOLEAN -> V2DataType.BOOLEAN
            PrimitiveType.BYTE -> V2DataType.BYTE
            PrimitiveType.SHORT -> V2DataType.SHORT
            PrimitiveType.INT -> V2DataType.INT
            PrimitiveType.LONG -> V2DataType.LONG
            PrimitiveType.FLOAT -> V2DataType.FLOAT
            PrimitiveType.DOUBLE -> V2DataType.DOUBLE
            PrimitiveType.STRING -> V2DataType.STRING
            PrimitiveType.OBJECT -> V2DataType.JSON
        }

    // endregion
}
