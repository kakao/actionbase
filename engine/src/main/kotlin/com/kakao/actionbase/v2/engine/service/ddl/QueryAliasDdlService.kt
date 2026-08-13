package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.QueryAliasEntity
import com.kakao.actionbase.v2.engine.label.Label

import reactor.core.publisher.Mono

class QueryAliasDdlService(
    graph: Graph,
    label: Label,
    factory: EntityFactory<QueryAliasEntity>,
) : DdlService<QueryAliasEntity, QueryAliasCreateRequest, QueryAliasUpdateRequest, QueryAliasDeleteRequest>(graph, label, factory) {
    override fun canDeactivate(name: EntityName): Mono<Boolean> = Mono.just(true)

    override fun toEntity(edge: HashEdge): QueryAliasEntity = QueryAliasEntity.toEntity(edge)

    override fun sync(): Mono<Void> = Mono.empty()
}

data class QueryAliasCreateRequest(
    val desc: String,
    val target: String,
    override val audit: Audit = Audit.default,
) : DdlRequest {
    override fun toEdge(name: EntityName): TraceEdge =
        QueryAliasEntity(
            active = true,
            name = name,
            desc = desc,
            target = target,
        ).toEdge()
}

data class QueryAliasUpdateRequest(
    val active: Boolean? = null,
    val desc: String? = null,
    val target: String? = null,
    override val audit: Audit = Audit.default,
) : DdlRequest {
    private fun toNotNullMap(): Map<String, Any> =
        buildMap {
            active?.let { put("props_active", it) }
            desc?.let { put("desc", it) }
            target?.let { put("target", it) }
        }

    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge(props = toNotNullMap())
}

data class QueryAliasDeleteRequest(
    override val audit: Audit = Audit.default,
) : DdlRequest {
    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge()
}
