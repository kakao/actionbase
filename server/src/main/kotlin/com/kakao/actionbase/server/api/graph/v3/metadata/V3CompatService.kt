package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest as V2AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasDeleteRequest as V2AliasDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasUpdateRequest as V2AliasUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest as V2LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelDeleteRequest as V2LabelDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest as V2LabelUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest as V2ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceDeleteRequest as V2ServiceDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest as V2ServiceUpdateRequest

import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.isReadOnlyInV2
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toAliasResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toDatabaseResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toLabelType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toTableResponse
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2DirectionType
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2EdgeSchema
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2Index
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus

import org.springframework.stereotype.Service

import reactor.core.publisher.Mono

@Service
class V3CompatService(
    private val graph: Graph,
) {
    // region Database CRUD (using V2 serviceDdl)

    fun getDatabase(database: String): Mono<DatabaseResponse> =
        graph.serviceDdl
            .getSingle(EntityName.fromOrigin(database))
            .map { it.toDatabaseResponse() }

    fun getDatabases(status: MetadataStatus = MetadataStatus.ACTIVE): Mono<DdlPage<DatabaseResponse>> =
        graph.serviceDdl
            .getAll(EntityName.origin)
            .map { page -> page.content.filter { status.matches(it.active) }.toPage { it.toDatabaseResponse() } }

    fun createDatabase(
        database: String,
        request: DatabaseCreateRequest,
    ): Mono<DdlStatus<DatabaseResponse>> {
        val v2Request = V2ServiceCreateRequest(desc = request.comment)
        return graph.serviceDdl
            .create(EntityName.fromOrigin(database), v2Request)
            .map { status -> status.mapResult { it.toDatabaseResponse() } }
    }

    fun updateDatabase(
        database: String,
        request: DatabaseUpdateRequest,
    ): Mono<DdlStatus<DatabaseResponse>> {
        val v2Request =
            V2ServiceUpdateRequest(
                active = request.active,
                desc = request.comment,
            )
        return graph.serviceDdl
            .update(EntityName.fromOrigin(database), v2Request)
            .map { status -> status.mapResult { it.toDatabaseResponse() } }
    }

    fun deleteDatabase(database: String): Mono<DdlStatus<DatabaseResponse>> =
        graph.serviceDdl
            .delete(EntityName.fromOrigin(database), V2ServiceDeleteRequest())
            .map { status -> status.mapResult { it.toDatabaseResponse() } }

    // endregion

    // region Table CRUD (using V2 labelDdl)

    fun getTable(
        database: String,
        table: String,
    ): Mono<TableResponse> =
        graph.labelDdl
            .getSingle(EntityName(database, table))
            .map { it.toTableResponse() }

    fun getTables(
        database: String,
        status: MetadataStatus = MetadataStatus.ACTIVE,
    ): Mono<DdlPage<TableResponse>> =
        graph.labelDdl
            .getAll(EntityName(database))
            .map { page -> page.content.filter { status.matches(it.active) }.toPage { it.toTableResponse() } }

    fun createTable(
        database: String,
        table: String,
        request: TableCreateRequest,
    ): Mono<DdlStatus<TableResponse>> {
        val v2Request =
            V2LabelCreateRequest(
                desc = request.comment,
                type = request.type.toLabelType(),
                schema = request.schema.toV2EdgeSchema(),
                dirType = request.direction.toV2DirectionType(),
                storage = request.storage,
                groups = request.groups,
                indices = request.indexes.map { it.toV2Index() },
                caches = request.caches,
                event = false,
                readOnly = request.type.isReadOnlyInV2(),
                mode = request.mode.toV2MutationMode(),
            )
        return graph.labelDdl
            .create(EntityName(database, table), v2Request)
            .map { status -> status.mapResult { it.toTableResponse() } }
    }

    fun updateTable(
        database: String,
        table: String,
        request: TableUpdateRequest,
    ): Mono<DdlStatus<TableResponse>> {
        val v2Request =
            V2LabelUpdateRequest(
                active = request.active,
                desc = request.comment,
                type = null,
                schema = request.schema?.toV2EdgeSchema(),
                groups = request.groups,
                indices = request.indexes?.map { it.toV2Index() },
                readOnly = null,
                mode = request.mode?.toV2MutationMode(),
                caches = request.caches,
            )
        return graph.labelDdl
            .update(EntityName(database, table), v2Request)
            .map { status -> status.mapResult { it.toTableResponse() } }
    }

    fun deleteTable(
        database: String,
        table: String,
    ): Mono<DdlStatus<TableResponse>> =
        graph.labelDdl
            .delete(EntityName(database, table), V2LabelDeleteRequest())
            .map { status -> status.mapResult { it.toTableResponse() } }

    // endregion

    // region Alias CRUD (using V2 aliasDdl)

    fun getAlias(
        database: String,
        alias: String,
    ): Mono<AliasResponse> =
        graph.aliasDdl
            .getSingle(EntityName(database, alias))
            .map { it.toAliasResponse() }

    fun getAliases(
        database: String,
        status: MetadataStatus = MetadataStatus.ACTIVE,
    ): Mono<DdlPage<AliasResponse>> =
        graph.aliasDdl
            .getAll(EntityName(database))
            .map { page -> page.content.filter { status.matches(it.active) }.toPage { it.toAliasResponse() } }

    fun createAlias(
        database: String,
        alias: String,
        request: AliasCreateRequest,
    ): Mono<DdlStatus<AliasResponse>> {
        val v2Request =
            V2AliasCreateRequest(
                desc = request.comment,
                target = "$database.${request.table}",
            )
        return graph.aliasDdl
            .create(EntityName(database, alias), v2Request)
            .map { status -> status.mapResult { it.toAliasResponse() } }
    }

    fun updateAlias(
        database: String,
        alias: String,
        request: AliasUpdateRequest,
    ): Mono<DdlStatus<AliasResponse>> {
        val v2Request =
            V2AliasUpdateRequest(
                active = request.active,
                desc = request.comment,
                target = request.table?.let { "$database.$it" },
            )
        return graph.aliasDdl
            .update(EntityName(database, alias), v2Request)
            .map { status -> status.mapResult { it.toAliasResponse() } }
    }

    fun deleteAlias(
        database: String,
        alias: String,
    ): Mono<DdlStatus<AliasResponse>> =
        graph.aliasDdl
            .delete(EntityName(database, alias), V2AliasDeleteRequest())
            .map { status -> status.mapResult { it.toAliasResponse() } }

    // endregion
}
