package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.deprecated.DeprecatedEdgeSchema
import com.kakao.actionbase.v2.engine.label.DatastoreHashLabel
import com.kakao.actionbase.v2.engine.label.DatastoreIndexedLabel
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.label.metastore.JdbcHashLabel
import com.kakao.actionbase.v2.engine.label.metastore.LocalBackedJdbcHashLabel
import com.kakao.actionbase.v2.engine.label.nil.NilLabel
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.storage.DatastoreStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.jdbc.JdbcStorage
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.util.getLogger

import org.slf4j.Logger

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class LabelEntity(
    override val active: Boolean,
    override val name: EntityName,
    val desc: String,
    val type: LabelType,
    val schema: EdgeSchema,
    val dirType: DirectionType,
    val storage: String,
    val indices: List<Index> = emptyList(),
    val groups: List<Group> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val event: Boolean = false,
    val readOnly: Boolean = false,
    val mode: MutationMode = MutationMode.SYNC,
) : EdgeEntity {
    @JsonIgnore
    val id = ValueUtils.stringHash(name.fullQualifiedName)

    init {
        require(type.edgeType != EdgeType.HASH || dirType == DirectionType.OUT) {
            "The hash LabelV0 $type supports only OUT direction. Input direction: $dirType"
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun materialize(
        graph: GraphDefaults,
        block: Label.() -> Unit = {},
    ): Label {
        return runCatching {
            if (type == LabelType.NIL) return NilLabel(this)

            val storageEntity = graph.getStorage(storage)!!
            val storage = storageEntity.materialize()
            when (type) {
                LabelType.HASH -> {
                    when (storage) {
                        is LocalStorage -> LocalBackedJdbcHashLabel.create(this, graph, storage, block)
                        is JdbcStorage -> JdbcHashLabel.create(this, graph, storage, block)
                        is HBaseStorage -> HBaseHashLabel.create(this, graph, storage)
                        is DatastoreStorage -> DatastoreHashLabel.create(this, graph, block)
                        else -> {
                            logger.error(
                                "{} supports only Local, Jdbc, HBase storage types. {} is not supported. Fallback to NilLabel",
                                type,
                                storage,
                            )
                            NilLabel(this.copy(type = LabelType.NIL))
                        }
                    }
                }
                LabelType.INDEXED, LabelType.MULTI_EDGE -> {
                    // MultiEdge is a variant of the INDEXED type in the v2 engine.
                    if (type == LabelType.MULTI_EDGE && !readOnly) {
                        logger.error("MULTI_EDGE type should be read-only in the v2 engine. Fallback to NilLabel")
                        return NilLabel(this.copy(type = LabelType.NIL))
                    }

                    when (storage) {
                        is HBaseStorage -> HBaseIndexedLabel.create(this, graph, storage)
                        is DatastoreStorage -> DatastoreIndexedLabel.create(this, graph, block)
                        else -> {
                            logger.error(
                                "{} supports only Jdbc, HBase storage types. {} is not supported. Fallback to NilLabel",
                                type,
                                storage,
                            )
                            NilLabel(this.copy(type = LabelType.NIL))
                        }
                    }
                }
                LabelType.IMMUTABLE_INDEXED -> {
                    logger.error("{} supports only ... types. {} fallback to NilLabel", type, storage)
                    NilLabel(this.copy(type = LabelType.NIL))
                }
                LabelType.NIL -> NilLabel(this)
            }
        }.getOrElse { ex ->
            ex.printStackTrace()
            NilLabel(this.copy(type = LabelType.NIL)).also {
                logger.error("Failed to materialize label: {}, fallback to NilLabel\n{}", name, it)
            }
        }
    }

    override fun toEdge(): TraceEdge =
        name.toTraceEdge(
            props =
                mapOf(
                    "desc" to desc,
                    "type" to type.name,
                    "schema" to objectMapper.writeValueAsString(schema),
                    "dirType" to dirType.name,
                    "storage" to storage,
                    "event" to event,
                    "groups" to objectMapper.writeValueAsString(groups),
                    "indices" to objectMapper.writeValueAsString(indices),
                    "caches" to objectMapper.writeValueAsString(caches),
                    "readOnly" to readOnly,
                    "mode" to mode.name,
                ),
        )

    fun toCreateRequest(): LabelCreateRequest =
        LabelCreateRequest(
            desc = desc,
            type = type,
            schema = schema,
            dirType = dirType,
            storage = storage,
            groups = groups,
            indices = indices,
            event = event,
            readOnly = readOnly,
            mode = mode,
        )

    companion object : EntityFactory<LabelEntity> {
        private val objectMapper =
            jacksonObjectMapper().apply {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }

        private val logger: Logger = getLogger()

        fun tryParseSchema(schemaString: String): EdgeSchema =
            try {
                objectMapper.readValue(schemaString)
            } catch (_: MismatchedInputException) {
                objectMapper.readValue<DeprecatedEdgeSchema>(schemaString).toEdgeSchema()
            }

        override fun toEntity(edge: HashEdge): LabelEntity =
            LabelEntity(
                active = (edge.props.getOrDefault("props_active", null) ?: true).toString().toBoolean(),
                name = EntityName.withPhase(edge.src.toString(), edge.tgt.toString()),
                desc = edge.props["desc"].toString(),
                type =
                    LabelType.of(edge.props["type"].toString()) ?: LabelType.NIL.also {
                        logger.warn("Unknown label type: {}", edge.props["type"])
                    },
                schema = tryParseSchema(edge.props["schema"].toString()),
                dirType =
                    DirectionType.of(edge.props["dirType"].toString()) ?: DirectionType.BOTH.also {
                        logger.warn("Unknown direction type: {}", edge.props["dirType"])
                    },
                storage = edge.props["storage"].toString(),
                groups = objectMapper.readValue(edge.props["groups"]?.toString() ?: "[]"),
                indices = objectMapper.readValue(edge.props["indices"].toString()),
                caches = objectMapper.readValue(edge.props["caches"]?.toString() ?: "[]"),
                event = edge.props["event"].toString().toBoolean(),
                readOnly = edge.props["readOnly"].toString().toBoolean(),
                mode =
                    MutationMode.valueOf(
                        edge.props.getOrDefault("mode", MutationMode.SYNC.name).toString(),
                    ),
            )

        override fun toEntity(row: RowWithSchema): LabelEntity =
            LabelEntity(
                active = (row.getOrNull("props_active") ?: true).toString().toBoolean(),
                name = EntityName.withPhase(row.getString("src"), row.getString("tgt")),
                desc = row.getString("desc"),
                type =
                    LabelType.of(row.getString("type")) ?: LabelType.NIL.also {
                        logger.warn("Unknown label type: {}", row.getString("type"))
                    },
                schema = tryParseSchema(row.getString("schema")),
                dirType =
                    DirectionType.of(row.getString("dirType")) ?: DirectionType.BOTH.also {
                        logger.warn("Unknown direction type: {}", row.getString("dirType"))
                    },
                storage = row.getString("storage"),
                groups = objectMapper.readValue(row.getOrNull("groups")?.toString() ?: "[]"),
                indices = objectMapper.readValue(row.getString("indices")),
                caches = objectMapper.readValue(row.getOrNull("caches")?.toString() ?: "[]"),
                event = DataType.BOOLEAN.cast(row.getOrNull("event"))?.let { it as Boolean } ?: false,
                readOnly = row.getBoolean("readOnly"),
                mode = MutationMode.valueOf((row.getOrNull("mode") ?: MutationMode.SYNC.name).toString()),
            )

        @JvmStatic
        @JsonCreator
        fun create(
            @JsonProperty("active", required = false) active: Boolean = true,
            @JsonProperty("name") fullName: String,
            @JsonProperty("desc") desc: String,
            @JsonProperty("type") type: LabelType,
            @JsonProperty("schema") schema: EdgeSchema,
            @JsonProperty("dirType") dirType: DirectionType,
            @JsonProperty("storage") storage: String,
            @JsonProperty("groups", required = false) groups: List<Group> = emptyList(),
            @JsonProperty("indices", required = false) indices: List<Index> = emptyList(),
            @JsonProperty("caches", required = false) caches: List<Cache> = emptyList(),
            @JsonProperty("event", required = false) event: Boolean = false,
            @JsonProperty("readOnly", required = false) readOnly: Boolean = false,
            @JsonProperty("mode", required = false) mode: MutationMode = MutationMode.SYNC,
        ): LabelEntity =
            LabelEntity(
                active,
                EntityName.of(fullName),
                desc,
                type,
                schema,
                dirType,
                storage,
                indices,
                groups,
                caches,
                event,
                readOnly,
                mode,
            )
    }

    override fun toString(): String = "LabelEntity(name=$name, desc='$desc', type=$type, schema=$schema, dirType=$dirType, storage='$storage', indices=$indices, event=$event, readOnly=$readOnly, mode=$mode)"
}
