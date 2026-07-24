package com.kakao.actionbase.v2.engine.metadata.sync

import com.kakao.actionbase.v2.core.code.hbase.ValueUtils
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.engine.entity.EdgeEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.sql.RowWithSchema

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class MetadataSyncStatus(
    val data: List<HostMetadataSyncStatus>,
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun <MetaType : EdgeEntity> getMetadataSyncStatus(
            onMetastore: DdlPage<MetaType>,
            onHosts: List<RowWithSchema>,
        ): MetadataSyncStatus {
            // True(reference) status
            // (service1, 1), (service2, 2)
            val referenceEntityNameHash: Map<EntityName, Int> =
                onMetastore.content.associate { entity ->
                    val name = entity.name
                    val hash = normalizedHashValue(entity)
                    name to hash
                }

            val latestOnHosts: List<RowWithSchema> =
                onHosts
                    .groupBy { row ->
                        val src = row.getString(EdgeSchema.Fields.SRC)
                        val tgt = row.getString(EdgeSchema.Fields.TGT)
                        Pair(src, tgt)
                    }.map { (_, rows) -> rows.maxBy { row -> row.getLong(EdgeSchema.Fields.TS) } }

            // (host, ((service1, 1), (service2, 2))
            val hostEntityNameProps: List<Pair<Pair<String, String>, Map<EntityName, MetadataSyncEntity.Props>>> =
                latestOnHosts
                    .groupBy { row ->
                        // group by host
                        val tgt = MetadataSyncEntity.Tgt.of(row)
                        tgt.hostName to tgt.commitId
                    }.map { (hostCommitIdPair, rows) ->
                        val entityNameProps =
                            rows.associate { row ->
                                val entityName = MetadataSyncEntity.Tgt.of(row).entityName
                                val props = MetadataSyncEntity.Props.of(row)
                                entityName to props
                            }
                        hostCommitIdPair to entityNameProps
                    }

            val data =
                hostEntityNameProps
                    .map { (hostCommitIdPair, entityNameProps) ->
                        val metadata =
                            referenceEntityNameHash
                                .map { (entityName, referenceHash) ->
                                    val status =
                                        when {
                                            entityNameProps.containsKey(entityName).not() -> SyncStatus.NOT_EXIST
                                            entityNameProps[entityName]?.hash == referenceHash -> SyncStatus.SYNC
                                            else -> SyncStatus.UNSYNC
                                        }
                                    MetadataSyncEntityStatus(
                                        name = entityName,
                                        status = status,
                                    )
                                }
                        HostMetadataSyncStatus(
                            host = hostCommitIdPair.first,
                            commitId = hostCommitIdPair.second,
                            entities = metadata,
                        )
                    }

            return MetadataSyncStatus(data)
        }

        fun normalizedHashValue(entity: EdgeEntity): Int {
            val serializedString = objectMapper.writeValueAsString(entity)
            return ValueUtils.stringHash(serializedString)
        }

        fun normalizeHashValue(inputString: String): Int {
            val tree = objectMapper.readTree(inputString)
            val normalizedString = objectMapper.writeValueAsString(tree)
            return ValueUtils.stringHash(normalizedString)
        }
    }
}

data class HostMetadataSyncStatus(
    val host: String,
    val commitId: String,
    val entities: List<MetadataSyncEntityStatus>,
)

data class MetadataSyncEntityStatus(
    @JsonIgnore
    val name: EntityName,
    val status: SyncStatus,
) {
    @get:JsonProperty("name")
    val repr: String
        get() = name.fullQualifiedName
}

enum class SyncStatus {
    SYNC,
    UNSYNC,
    NOT_EXIST,
}

enum class MetadataType {
    SERVICE,
    STORAGE,
    LABEL,
    ALIAS,
    ;

    companion object {
        private val NAME_TO_VALUE_MAP: Map<String, MetadataType> = entries.associateBy { it.name }

        fun of(name: String): MetadataType = NAME_TO_VALUE_MAP[name] ?: throw IllegalArgumentException("Unknown MetadataType: $name")
    }
}
