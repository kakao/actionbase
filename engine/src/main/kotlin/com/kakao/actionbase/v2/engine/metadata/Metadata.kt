package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.engine.EngineConstants
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.entity.ServiceEntity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Suppress("PropertyName")
object Metadata {
    val jackson = jacksonObjectMapper()

    const val origin = "origin"

    const val sysServiceName = "sys"

    const val defaultHBaseStorageName = "default_hbase_storage"

    const val sysServiceLabelName = "service"

    const val sysStorageLabelName = "storage"

    const val sysLabelLabelName = "label"

    const val sysInfoLabelName = "info"

    const val sysAliasLabelName = "alias"

    const val heartBeatLabelName = "heartbeat"

    const val sysQueryLabelName = "query"

    const val sysQueryAliasLabelName = "query_alias"

    const val sysOnlineMetadataLabelV2Name = "online_metadata_v2"

    const val sysNilLabelName = "nil"

    val heartBeatEntityName = EntityName(sysServiceName, heartBeatLabelName)

    val sysServiceEntity =
        ServiceEntity(
            active = true,
            name = EntityName.fromOrigin(sysServiceName),
            desc = "System service",
        )

    val serviceLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysServiceLabelName),
            desc = "System service label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "origin"),
                    VertexField(VertexType.STRING, "{{serviceName}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val storageLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysStorageLabelName),
            desc = "System storage label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "origin"),
                    VertexField(VertexType.STRING, "{{storageName}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("type", DataType.STRING, false),
                        Field("conf", DataType.STRING, false),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val labelLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysLabelLabelName),
            desc = "System label label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "{{service}}"),
                    VertexField(VertexType.STRING, "{{label}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("type", DataType.STRING, false),
                        Field("schema", DataType.STRING, false),
                        Field("dirType", DataType.STRING, false),
                        Field("storage", DataType.STRING, false),
                        Field("groups", DataType.STRING, true),
                        Field("indices", DataType.STRING, false),
                        Field("caches", DataType.STRING, true),
                        Field("event", DataType.BOOLEAN, false),
                        Field("readOnly", DataType.BOOLEAN, false),
                        Field("mode", DataType.STRING, true),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val infoLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysInfoLabelName),
            desc = "System info label",
            type = LabelType.NIL,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "origin"),
                    VertexField(VertexType.STRING, "{{info logger}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true, null),
                        Field("message", DataType.STRING, false, null),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = "",
        )

    val aliasLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysAliasLabelName),
            desc = "System alias label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "{{service}}"),
                    VertexField(VertexType.STRING, "{{alias}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("target", DataType.STRING, false),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val queryLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysQueryLabelName),
            desc = "System query label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "origin"),
                    VertexField(VertexType.STRING, "{{queryId}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("arguments", DataType.STRING, true),
                        Field("fetch", DataType.STRING, false),
                        Field("transform", DataType.STRING, true),
                        Field("stats", DataType.STRING, true),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val queryAliasLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysQueryAliasLabelName),
            desc = "System query alias label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "origin"),
                    VertexField(VertexType.STRING, "{{alias}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("target", DataType.STRING, false),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = EngineConstants.METASTORE_URI,
        )

    val onlineMetadataLabelV2Entity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysOnlineMetadataLabelV2Name),
            desc = "Online metadata label v2",
            type = LabelType.INDEXED,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "{{phase}}:{{metadata_type}}"),
                    VertexField(VertexType.STRING, "{{hostName}}:{{commitId}}:{{entityName}}"),
                    listOf(
                        Field("hash", DataType.INT, true),
                    ),
                ),
            indices =
                listOf(
                    Index("ts_desc", listOf(Index.Field("ts", Order.DESC))),
                ),
            dirType = DirectionType.OUT,
            storage = defaultHBaseStorageName,
        )

    val sysNilLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysNilLabelName),
            desc = "System nil label",
            type = LabelType.NIL,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "{{any}}"),
                    VertexField(VertexType.STRING, "{{any}}"),
                    listOf(),
                ),
            dirType = DirectionType.OUT,
            storage = "",
        )
}
