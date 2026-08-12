package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.QueryEntity
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.StatKey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import reactor.core.publisher.Mono

class QueryDdlService(
    graph: Graph,
    label: Label,
    factory: EntityFactory<QueryEntity>,
) : DdlService<QueryEntity, QueryCreateRequest, QueryUpdateRequest, QueryDeleteRequest>(graph, label, factory) {
    override fun canDeactivate(name: EntityName): Mono<Boolean> = Mono.just(true)

    override fun toEntity(edge: HashEdge): QueryEntity = QueryEntity.toEntity(edge)

    override fun sync(): Mono<Void> = Mono.empty()
}

data class QueryCreateRequest(
    val desc: String,
    val arguments: List<StructField>,
    val fetch: JsonNode,
    val transform: JsonNode,
    val stats: Set<StatKey> = emptySet(),
    override val audit: Audit = Audit.default,
) : DdlRequest {
    override fun toEdge(name: EntityName): TraceEdge =
        QueryEntity(
            active = true,
            name = name,
            desc = desc,
            arguments = arguments,
            fetch = fetch,
            transform = transform,
            stats = stats,
        ).toEdge()
}

data class QueryUpdateRequest(
    val active: Boolean? = null,
    val desc: String? = null,
    val arguments: List<StructField>? = null,
    val fetch: JsonNode? = null,
    val transform: JsonNode? = null,
    val stats: Set<StatKey>? = null,
    override val audit: Audit = Audit.default,
) : DdlRequest {
    private fun toNotNullMap(): Map<String, Any> =
        buildMap {
            active?.let { put("props_active", it) }
            desc?.let { put("desc", it) }
            arguments?.let { put("arguments", objectMapper.writeValueAsString(it)) }
            fetch?.let { put("fetch", objectMapper.writeValueAsString(it)) }
            transform?.let { put("transform", objectMapper.writeValueAsString(it)) }
            stats?.let { put("stats", it.joinToString(",") { key -> key.name }) }
        }

    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge(props = toNotNullMap())

    private companion object {
        val objectMapper = jacksonObjectMapper()
    }
}

data class QueryDeleteRequest(
    override val audit: Audit = Audit.default,
) : DdlRequest {
    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge()
}
