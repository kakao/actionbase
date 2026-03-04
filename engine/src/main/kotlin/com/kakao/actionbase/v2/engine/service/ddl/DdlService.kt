package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.v2.core.code.EmptyEdgeIdEncoder
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.entity.EdgeEntity
import com.kakao.actionbase.v2.engine.entity.EntityFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.service.ddl.DdlExceptionMessage.entityNotDeactivatable
import com.kakao.actionbase.v2.engine.service.ddl.DdlExceptionMessage.entityNotDeactivate
import com.kakao.actionbase.v2.engine.service.ddl.DdlExceptionMessage.serviceNotExists
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

abstract class DdlService<Entity : EdgeEntity, Create : DdlRequest, Update : DdlRequest, Delete : DdlRequest>(
    protected val graph: Graph,
    private val label: Label,
    private val factory: EntityFactory<Entity>,
) {
    fun createMetadataOnlyForTest(
        name: EntityName,
        request: Create,
    ): Mono<MutationResult> {
        val edge = request.toEdge(name)
        return graph.mutate(label.name, label, listOf(edge), EdgeOperation.INSERT, mode = MutationMode.SYNC, force = true)
    }

    fun create(
        name: EntityName,
        request: Create,
    ): Mono<DdlStatus<Entity>> =
        checkCreatePrecondition(name, request)
            .flatMap {
                if (it.isEmpty()) {
                    val edge = request.toEdge(name)
                    // mutate should call to write WAL
                    graph
                        .mutate(
                            label.name,
                            label,
                            listOf(edge),
                            EdgeOperation.INSERT,
                            audit = request.audit,
                            mode = MutationMode.SYNC,
                            force = true,
                            failOnExist = true,
                        ).map {
                            val result = it.result.first()
                            DdlStatus.fromEdgeOperationStatus(
                                result.status,
                                if (result.edge?.active == Active.ACTIVE) toEntity(result.edge) else null,
                            )
                        }.flatMap { ddlResult ->
                            sync().thenReturn(ddlResult)
                        }.defaultIfEmpty(DdlStatus.fromEdgeOperationStatus(EdgeOperationStatus.IDLE))
                } else {
                    Mono.error(
                        IllegalArgumentException(
                            DdlStatus<Entity>(
                                DdlStatus.Status.BAD_REQUEST,
                                message = "ddl create failed...\n${it.joinToString("\n") }",
                            ).toString(),
                        ),
                    )
                }
            }

    fun update(
        name: EntityName,
        request: Update,
    ): Mono<DdlStatus<Entity>> {
        // mutate should call to write WAL
        return checkUpdatePrecondition(name, request)
            .flatMap {
                if (it.isEmpty()) {
                    val edge = request.toEdge(name)
                    graph
                        .mutate(
                            label.name,
                            label,
                            listOf(edge),
                            EdgeOperation.UPDATE,
                            audit = request.audit,
                            mode = MutationMode.SYNC,
                            force = true,
                        ).map {
                            val result = it.result.first()
                            DdlStatus.fromEdgeOperationStatus(
                                result.status,
                                if (result.edge?.active == Active.ACTIVE) toEntity(result.edge) else null,
                            )
                        }.flatMap { ddlResult ->
                            sync().thenReturn(ddlResult)
                        }.defaultIfEmpty(DdlStatus.fromEdgeOperationStatus(EdgeOperationStatus.IDLE))
                } else {
                    Mono.error(
                        IllegalArgumentException(
                            DdlStatus<Entity>(
                                DdlStatus.Status.BAD_REQUEST,
                                message = "ddl update failed...\n${it.joinToString("\n") }",
                            ).toString(),
                        ),
                    )
                }
            }.defaultIfEmpty(DdlStatus.fromEdgeOperationStatus(EdgeOperationStatus.IDLE))
    }

    fun delete(
        name: EntityName,
        request: Delete,
    ): Mono<DdlStatus<Entity>> {
        val edge = request.toEdge(name)
        // mutate should call to write WAL
        return checkDeletePrecondition(name, request).flatMap {
            if (it.isEmpty()) {
                graph
                    .mutate(
                        label.name,
                        label,
                        listOf(edge),
                        EdgeOperation.DELETE,
                        audit = request.audit,
                        mode = MutationMode.SYNC,
                        force = true,
                    ).map {
                        val result = it.result.first()
                        DdlStatus.fromEdgeOperationStatus(
                            result.status,
                            if (result.edge?.active == Active.ACTIVE) toEntity(result.edge) else null,
                        )
                    }.flatMap { ddlResult ->
                        sync().thenReturn(ddlResult)
                    }.defaultIfEmpty(DdlStatus.fromEdgeOperationStatus(EdgeOperationStatus.IDLE))
            } else {
                Mono.error(
                    IllegalArgumentException(
                        DdlStatus<Entity>(
                            DdlStatus.Status.BAD_REQUEST,
                            message = "ddl create failed...\n${it.joinToString("\n") }",
                        ).toString(),
                    ),
                )
            }
        }
    }

    fun copy(
        from: EntityName,
        to: EntityName,
        props: Map<String, String>,
    ): Mono<DdlStatus<Entity>> =
        getSingle(from)
            .flatMap {
                val fromEdge = it.toEdge()
                val edge =
                    Edge(
                        fromEdge.ts,
                        to.phaseServiceName,
                        to.name,
                        fromEdge.props + props,
                    ).toTraceEdge()
                graph
                    .mutate(label.name, label, listOf(edge), EdgeOperation.INSERT, mode = MutationMode.SYNC, force = true)
                    .map {
                        val result = it.result.first()
                        DdlStatus.fromEdgeOperationStatus(
                            result.status,
                            if (result.edge?.active == Active.ACTIVE) toEntity(result.edge) else null,
                        )
                    }.flatMap { ddlResult ->
                        sync().thenReturn(ddlResult)
                    }
            }.defaultIfEmpty(DdlStatus.fromEdgeOperationStatus(EdgeOperationStatus.IDLE))

    fun getAll(name: EntityName): Mono<DdlPage<Entity>> {
        val scanFilter =
            ScanFilter(
                name = label.name,
                srcSet = setOf(name.phaseServiceName),
                limit = graph.metadataFetchLimit,
            )
        return label
            .scan(scanFilter, emptySet(), EmptyEdgeIdEncoder.INSTANCE)
            .map {
                factory.fromDataFrame(it)
            }.map {
                DdlPage(it.size.toLong(), it)
            }
    }

    fun getSingle(name: EntityName): Mono<Entity> =
        label
            .get(
                name.phaseServiceName,
                name.nameNotNull,
                Direction.OUT,
                emptySet(),
                EmptyEdgeIdEncoder.INSTANCE,
            ).map {
                factory.fromDataFrame(it)
            }.mapNotNull {
                it.firstOrNull()
            }

    /**
     *     |---------|-------------------------|---------|---------|------------------------------------|-------|------------------------------|
     *     | Action  | Common                  | Service | Storage | Label                              | Query | Alias                        |
     *     |---------|-------------------------|---------|---------|------------------------------------|-------|------------------------------|
     *     | Create  | Check service exists    | -       | -       | Check name not exists              | -     | Check name not exists        |
     *     |         |                         |         |         |------------------------------------|       |------------------------------|
     *     |         |                         |         |         | Check storage exists               |       | Check target name exists     |
     *     |---------|-------------------------|---------|---------|------------------------------------|-------|------------------------------|
     *     | Update  | Check active updatable  | -       | -       | check add optional fields only     | -     | -                            |
     *     |---------|-------------------------|---------|---------|------------------------------------|-------|------------------------------|
     *     | Delete  | Check active is false   | -       | -       | -                                  | -     | -                            |
     *     |---------|-------------------------|---------|---------|------------------------------------|-------|------------------------------|
     *
     **/

    open fun createPreconditions(
        name: EntityName,
        request: Create,
    ): Array<Mono<List<String>>> = emptyArray()

    open fun updatePreconditions(
        name: EntityName,
        request: Update,
    ): Array<Mono<List<String>>> = emptyArray()

    open fun deletePreconditions(
        name: EntityName,
        request: Delete,
    ): Array<Mono<List<String>>> = emptyArray()

    private fun checkCreatePrecondition(
        name: EntityName,
        request: Create,
    ): Mono<List<String>> =
        Flux
            .merge(
                graph.checkServiceExists(name).map { if (it) listOf() else listOf(serviceNotExists(name.service)) },
                *createPreconditions(name, request),
            ).reduce { acc, list -> acc + list }
            .defaultIfEmpty(listOf())

    private fun checkUpdatePrecondition(
        name: EntityName,
        request: Update,
    ): Mono<List<String>> =
        Flux
            .merge(
                canUpdateActive(name, request).map { if (it) listOf() else listOf(entityNotDeactivatable(name)) },
                *updatePreconditions(name, request),
            ).reduce { acc, list -> acc + list }
            .defaultIfEmpty(listOf())

    private fun checkDeletePrecondition(
        name: EntityName,
        request: Delete,
    ): Mono<List<String>> =
        Flux
            .merge(
                this.getSingle(name).map { if (!it.active) listOf() else listOf(entityNotDeactivate(name)) },
                *deletePreconditions(name, request),
            ).reduce { acc, list -> acc + list }
            .defaultIfEmpty(listOf())

    private fun canUpdateActive(
        name: EntityName,
        request: Update,
    ): Mono<Boolean> {
        // label uses props_active, other metadata uses active
        return Mono
            .just(request.toEdge(name))
            .mapNotNull {
                it.props.get("active") ?: it.props.get("props_active") as Boolean?
            }.defaultIfEmpty(true)
            .flatMap {
                if (it == false) {
                    canDeactivate(name)
                } else {
                    Mono.just(true)
                }
            }
    }

    abstract fun canDeactivate(name: EntityName): Mono<Boolean>

    abstract fun toEntity(edge: HashEdge): Entity

    abstract fun sync(): Mono<Void>

    companion object {
        const val DEFAULT_METADATA_LIMIT = 1_000
    }
}

data class DdlResult<ResultEntity>(
    val status: EdgeOperationStatus,
    val result: ResultEntity? = null,
)

data class DdlPage<ResultDTO>(
    val count: Long = 0,
    val content: List<ResultDTO>,
)
