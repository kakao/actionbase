package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.engine.EngineConstants
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.service.ddl.DdlExceptionMessage.aliasNameAlreadyExists
import com.kakao.actionbase.v2.engine.service.ddl.DdlExceptionMessage.storageNotExists
import com.kakao.actionbase.v2.engine.util.objectMapper

import jakarta.validation.constraints.NotBlank
import reactor.core.publisher.Mono

class LabelDdlService(
    graph: Graph,
    label: Label,
    factory: EntityFactory<LabelEntity>,
) : DdlService<LabelEntity, LabelCreateRequest, LabelUpdateRequest, LabelDeleteRequest>(graph, label, factory) {
    override fun createPreconditions(
        name: EntityName,
        request: LabelCreateRequest,
    ): Array<Mono<List<String>>> =
        arrayOf(
            graph.checkAliasExists(name).map { if (!it) listOf() else listOf(aliasNameAlreadyExists(name)) },
            if (request.storage.startsWith(EngineConstants.DATASTORE_URI_PREFIX)) {
                Mono.just(listOf())
            } else {
                val storageName = EntityName.fromOrigin(request.storage)
                graph
                    .checkStorageExists(storageName)
                    .map { if (it) listOf() else listOf(storageNotExists(storageName)) }
            },
        )

    override fun updatePreconditions(
        name: EntityName,
        request: LabelUpdateRequest,
    ): Array<Mono<List<String>>> {
        val oldLabelMono = graph.labelDdl.getSingle(name)
        val addOnlyOptionalFields =
            oldLabelMono.map {
                if (request.schema == null) {
                    true
                } else {
                    val newFields = request.schema.fields.toHashSet()
                    val oldFields = it.schema.fields.toHashSet()
                    val diff = newFields - oldFields
                    diff.none { field -> !field.isNullable }
                }
            }
        return arrayOf(
            addOnlyOptionalFields.map { if (it) listOf() else listOf("Only optional fields can be added") },
        )
    }

    override fun canDeactivate(name: EntityName): Mono<Boolean> {
        val checkAlias =
            graph.aliasDdl
                .getAll(name)
                .map { it.content.none { alias -> alias.active && (alias.target == name) } }
        val checkQuery =
            graph.queryDdl
                .getAll(name)
                .map { it.content.none { query -> query.active && (name.fullQualifiedName in query.query) } }
        return checkAlias.zipWith(checkQuery).map { a -> a.t1 && a.t2 }
    }

    override fun toEntity(edge: HashEdge): LabelEntity = LabelEntity.toEntity(edge)

    override fun sync(): Mono<Void> = graph.updateLabels()
}

data class LabelCreateRequest(
    @field:NotBlank(message = "desc is required")
    val desc: String,
    val type: LabelType,
    val schema: EdgeSchema,
    val dirType: DirectionType,
    val storage: String,
    val groups: List<Group> = emptyList(),
    val indices: List<Index> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val event: Boolean = false,
    val readOnly: Boolean = false,
    val mode: MutationMode = MutationMode.SYNC,
    override val audit: Audit = Audit.default,
) : DdlRequest {
    private fun toEntity(name: EntityName): LabelEntity =
        LabelEntity(
            active = true,
            name = name,
            desc = desc,
            type = type,
            schema = schema,
            dirType = dirType,
            storage = storage,
            groups = groups,
            indices = indices,
            caches = caches,
            event = event,
            readOnly = readOnly,
            mode = mode,
        )

    override fun toEdge(name: EntityName): TraceEdge = toEntity(name).toEdge()
}

data class LabelUpdateRequest(
    val active: Boolean?,
    val desc: String?,
    val type: LabelType?,
    val schema: EdgeSchema?,
    val groups: List<Group>?,
    val indices: List<Index>?,
    val readOnly: Boolean?,
    val mode: MutationMode?,
    val caches: List<Cache>?,
    override val audit: Audit = Audit.default,
) : DdlRequest {
    private fun toNotNullMap(): Map<String, Any> =
        buildMap {
            active?.let { put("props_active", it) }
            desc?.let { put("desc", it) }
            type?.let { put("type", it.name) }
            readOnly?.let { put("readOnly", it) }
            mode?.let { put("mode", it.name) }
            schema?.let { put("schema", objectMapper.writeValueAsString(it)) }
            groups?.let { put("groups", objectMapper.writeValueAsString(it)) }
            indices?.let { put("indices", objectMapper.writeValueAsString(it)) }
            caches?.let { put("caches", objectMapper.writeValueAsString(it)) }
        }

    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge(props = toNotNullMap())
}

data class LabelDeleteRequest(
    override val audit: Audit = Audit.default,
) : DdlRequest {
    override fun toEdge(name: EntityName): TraceEdge = name.toTraceEdge()
}

data class LabelCopyRequest(
    val target: String,
    val storage: String,
)
