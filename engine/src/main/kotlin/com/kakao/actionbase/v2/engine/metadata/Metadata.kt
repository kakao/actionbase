package com.kakao.actionbase.v2.engine.metadata

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
import com.kakao.actionbase.v2.engine.entity.StorageEntity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Suppress("PropertyName")
object Metadata {
    val jackson = jacksonObjectMapper()

    const val origin = "origin"

    const val sysServiceName = "sys"

    const val localOnlyStorageName = "local_only_storage"

    const val localBackedMetastoreName = "local_backed_metastore"

    const val metastoreName = "metastore"

    const val defaultHBaseStorageName = "default_hbase_storage"

    const val sysServiceLabelName = "service"

    const val sysStorageLabelName = "storage"

    const val sysLabelLabelName = "label"

    const val sysInfoLabelName = "info"

    const val sysQueryLabelName = "query"

    const val sysAliasLabelName = "alias"

    const val heartBeatLabelName = "heartbeat"

    const val sysOnlineMetadataLabelName = "online_metadata"

    const val sysOnlineMetadataLabelV2Name = "online_metadata_v2"

    const val sysNilLabelName = "nil"

    val heartBeatEntityName = EntityName(sysServiceName, heartBeatLabelName)

    val localOnlyStorageEntity =
        StorageEntity(
            active = true,
            name = EntityName.fromOrigin(localOnlyStorageName),
            desc = "local storage",
            type = StorageType.LOCAL,
            conf = jackson.createObjectNode().apply { put("useGlobal", false) },
        )

    val localBackedMetastoreEntity =
        StorageEntity(
            active = true,
            name = EntityName.fromOrigin(localBackedMetastoreName),
            desc = "Local backed JDBC storage",
            type = StorageType.LOCAL,
            conf = jackson.createObjectNode().apply { put("useGlobal", true) },
        )

    val metastoreStorageEntity =
        StorageEntity(
            active = true,
            name = EntityName.fromOrigin(metastoreName),
            desc = "Metastore storage",
            type = StorageType.JDBC,
            conf = jackson.createObjectNode(),
        )

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
            storage = localBackedMetastoreName,
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
            storage = localBackedMetastoreName,
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
            storage = localBackedMetastoreName,
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

    val queryLabelEntity =
        LabelEntity(
            active = true,
            name = EntityName(sysServiceName, sysQueryLabelName),
            desc = "System prepared query label",
            type = LabelType.HASH,
            schema =
                EdgeSchema(
                    VertexField(VertexType.STRING, "{{service}}"),
                    VertexField(VertexType.STRING, "{{query_name}}"),
                    listOf(
                        Field("props_active", DataType.BOOLEAN, true),
                        Field("desc", DataType.STRING, false),
                        Field("query", DataType.STRING, false),
                        Field("stats", DataType.STRING, false),
                    ),
                ),
            dirType = DirectionType.OUT,
            storage = metastoreName,
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
            storage = metastoreName,
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
