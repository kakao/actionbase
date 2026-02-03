package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.service.ddl.StorageCreateRequest
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.storage.DatastoreStorage
import com.kakao.actionbase.v2.engine.storage.Storage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.jdbc.JdbcStorage
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.storage.nil.NilStorage
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbStorage
import com.kakao.actionbase.v2.engine.util.getLogger

import org.slf4j.Logger

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class StorageEntity(
    override val active: Boolean,
    override val name: EntityName,
    val desc: String,
    val type: StorageType,
    val conf: JsonNode,
) : EdgeEntity {
    @get:JsonProperty("name")
    override val fullName: String
        get() = name.nameNotNull

    fun materialize(): Storage<*> =
        when (type) {
            StorageType.LOCAL -> {
                LocalStorage(this)
            }
            StorageType.JDBC -> {
                JdbcStorage(this)
            }
            StorageType.HBASE -> {
                HBaseStorage(this)
            }
            StorageType.SLATEDB -> {
                SlateDbStorage(this)
            }
            StorageType.DATASTORE -> {
                DatastoreStorage
            }
            else -> {
                NilStorage(this)
            }
        }

    override fun toEdge(): TraceEdge =
        name.toTraceEdge(
            props =
                mapOf(
                    "props_active" to active,
                    "desc" to desc,
                    "type" to type.name,
                    "conf" to conf.toJsonString(),
                ),
        )

    fun toCreateRequest(): StorageCreateRequest =
        StorageCreateRequest(
            desc = desc,
            type = type,
            conf = conf,
        )

    companion object : EntityFactory<StorageEntity> {
        private val mapper = jacksonObjectMapper()

        fun JsonNode.toJsonString(): String = mapper.writeValueAsString(this)

        private fun String.toJsonNode(): JsonNode = mapper.readTree(this)

        private val logger: Logger = getLogger()

        override fun toEntity(edge: HashEdge): StorageEntity =
            StorageEntity(
                active = (edge.props.getOrDefault("props_active", null) ?: true).toString().toBoolean(),
                name = EntityName.withPhase(edge.src.toString(), edge.tgt.toString()),
                desc = edge.props["desc"].toString(),
                type =
                    StorageType.of(edge.props["type"].toString()) ?: StorageType.NIL.also {
                        logger.warn("Unknown storage type: {}", edge.props["type"])
                    },
                conf = edge.props["conf"].toString().toJsonNode(),
            )

        override fun toEntity(row: RowWithSchema): StorageEntity =
            StorageEntity(
                active = (row.getOrNull("props_active") ?: true).toString().toBoolean(),
                name = EntityName.withPhase(row.getString("src"), row.getString("tgt")),
                desc = row.getString("desc"),
                type =
                    StorageType.of(row.getString("type")) ?: StorageType.NIL.also {
                        logger.warn("Unknown storage type: {}", row.getString("type"))
                    },
                conf = row.getString("conf").toJsonNode(),
            )

        val empty =
            StorageEntity(
                active = false,
                name = EntityName.origin,
                desc = "",
                type = StorageType.NIL,
                conf = mapper.createObjectNode(),
            )
    }
}
