package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order as V3Order
import com.kakao.actionbase.core.metadata.common.DirectionType as V3DirectionType
import com.kakao.actionbase.core.metadata.common.Index as V3Index
import com.kakao.actionbase.core.metadata.common.MutationMode as V3MutationMode
import com.kakao.actionbase.v2.core.code.Index as V2Index
import com.kakao.actionbase.v2.core.code.hbase.Order as V2Order
import com.kakao.actionbase.v2.core.metadata.DirectionType as V2DirectionType
import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode
import com.kakao.actionbase.v2.core.types.Field as V2Field

import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity

/**
 * Translates between the v2 metastore entities and the v3 metadata surface.
 *
 * The two carry the same information in the same structure; only the names differ. Every function
 * here is a rename, never a reshape.
 */
object V3MetadataConverter {
    // region Database (V3 DatabaseResponse <-> V2 ServiceEntity)

    fun ServiceEntity.toDatabaseResponse(): DatabaseResponse =
        DatabaseResponse(
            active = active,
            database = name.nameNotNull,
            comment = desc,
        )

    fun DatabaseResponse.toV2ServiceEntity(): ServiceEntity =
        ServiceEntity(
            active = active,
            name = EntityName.fromOrigin(database),
            desc = comment,
        )

    // endregion

    // region Table (V3 TableResponse <-> V2 LabelEntity)

    fun LabelEntity.toTableResponse(): TableResponse =
        TableResponse(
            active = active,
            database = name.service,
            table = name.nameNotNull,
            comment = desc,
            type = type.toTableType(),
            schema = schema.toTableSchema(),
            direction = dirType.toV3DirectionType(),
            storage = storage,
            indexes = indices.map { it.toV3Index() },
            groups = groups,
            caches = caches,
            mode = mode.toV3MutationMode(),
        )

    fun TableResponse.toV2LabelEntity(): LabelEntity =
        LabelEntity(
            active = active,
            name = EntityName(database, table),
            desc = comment,
            type = type.toLabelType(),
            schema = schema.toV2EdgeSchema(),
            dirType = direction.toV2DirectionType(),
            storage = storage,
            indices = indexes.map { it.toV2Index() },
            groups = groups,
            caches = caches,
            event = false,
            readOnly = type.isReadOnlyInV2(),
            mode = mode.toV2MutationMode(),
        )

    // endregion

    // region Alias (V3 AliasResponse <-> V2 AliasEntity)

    fun AliasEntity.toAliasResponse(): AliasResponse =
        AliasResponse(
            active = active,
            database = name.service,
            alias = name.nameNotNull,
            table = target.nameNotNull,
            comment = desc,
        )

    fun AliasResponse.toV2AliasEntity(): AliasEntity =
        AliasEntity(
            active = active,
            name = EntityName(database, alias),
            desc = comment,
            target = EntityName(database, table),
        )

    // endregion

    // region TableType conversion

    /**
     * v2's `HASH` and `NIL` have no v3 name. They read back as [TableType.EDGE], which is what the
     * v3 runtime already treats them as; neither can be created through the v3 surface.
     */
    fun LabelType.toTableType(): TableType =
        when (this) {
            LabelType.IMMUTABLE_INDEXED -> TableType.IMMUTABLE_EDGE
            LabelType.MULTI_EDGE -> TableType.MULTI_EDGE
            LabelType.VERTEX -> TableType.VERTEX
            LabelType.INDEXED, LabelType.HASH, LabelType.NIL -> TableType.EDGE
        }

    fun TableType.toLabelType(): LabelType =
        when (this) {
            TableType.EDGE -> LabelType.INDEXED
            TableType.IMMUTABLE_EDGE -> LabelType.IMMUTABLE_INDEXED
            TableType.MULTI_EDGE -> LabelType.MULTI_EDGE
            TableType.VERTEX -> LabelType.VERTEX
        }

    /** The v2 engine refuses to mutate a multi-edge label, so it must be stored as read-only. */
    fun TableType.isReadOnlyInV2(): Boolean = this == TableType.MULTI_EDGE

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

    // region Schema conversion (V3 TableSchema <-> V2 EdgeSchema)

    fun EdgeSchema.toTableSchema(): TableSchema =
        TableSchema(
            source = TableSchema.Key(type = src.type, comment = src.desc),
            target = TableSchema.Key(type = tgt.type, comment = tgt.desc),
            properties = fields.map { it.toTableSchemaProperty() },
        )

    fun TableSchema.toV2EdgeSchema(): EdgeSchema =
        EdgeSchema(
            VertexField(source.type, source.comment),
            VertexField(target.type, target.comment),
            properties.map { it.toV2Field() },
        )

    // endregion

    // region Field conversion

    fun V2Field.toTableSchemaProperty(): TableSchema.Property =
        TableSchema.Property(
            name = name,
            type = type,
            nullable = isNullable,
            comment = desc,
        )

    fun TableSchema.Property.toV2Field(): V2Field = V2Field(name, type, nullable, comment)

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
}
