package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.sql.StatKey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class QueryEntity(
    override val active: Boolean,
    override val name: EntityName,
    val desc: String,
    val arguments: List<StructField> = emptyList(),
    val fetch: JsonNode,
    val transform: JsonNode = objectMapper.createArrayNode(),
    val stats: Set<StatKey> = emptySet(),
) : EdgeEntity {
    val id: String
        get() = name.nameNotNull

    override fun toEdge(): TraceEdge =
        name.toTraceEdge(
            props =
                mapOf(
                    "props_active" to active,
                    "desc" to desc,
                    "arguments" to objectMapper.writeValueAsString(arguments),
                    "fetch" to objectMapper.writeValueAsString(fetch),
                    "transform" to objectMapper.writeValueAsString(transform),
                    "stats" to stats.joinToString(",") { it.name },
                ),
        )

    companion object : EntityFactory<QueryEntity> {
        internal val objectMapper = jacksonObjectMapper()

        override fun toEntity(edge: HashEdge): QueryEntity =
            QueryEntity(
                active = (edge.props.getOrDefault("props_active", null) ?: true).toString().toBoolean(),
                name = EntityName.withPhase(edge.src.toString(), edge.tgt.toString()),
                desc = edge.props["desc"].toString(),
                arguments = toArguments(edge.props["arguments"]?.toString()),
                fetch = toTree(edge.props["fetch"].toString()),
                transform = toTree(edge.props["transform"]?.toString()),
                stats = toStats(edge.props["stats"]?.toString()),
            )

        override fun toEntity(row: RowWithSchema): QueryEntity =
            QueryEntity(
                active = (row.getOrNull("props_active") ?: true).toString().toBoolean(),
                name = EntityName.withPhase(row.getString("src"), row.getString("tgt")),
                desc = row.getString("desc"),
                arguments = toArguments(row.getOrNull("arguments")?.toString()),
                fetch = toTree(row.getString("fetch")),
                transform = toTree(row.getOrNull("transform")?.toString()),
                stats = toStats(row.getOrNull("stats")?.toString()),
            )

        private fun toArguments(text: String?): List<StructField> = objectMapper.readValue(text?.takeIf { it.isNotBlank() } ?: "[]")

        private fun toTree(text: String?): JsonNode = objectMapper.readTree(text?.takeIf { it.isNotBlank() } ?: "[]")

        private fun toStats(text: String?): Set<StatKey> =
            text
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { StatKey.valueOf(it) }
                ?.toSet()
                ?: emptySet()
    }
}
