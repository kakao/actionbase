package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2AliasEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2LabelEntity
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2MutationMode
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV2StorageString
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3AliasDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3DatabaseDescriptor
import com.kakao.actionbase.server.api.graph.v3.metadata.V3MetadataConverter.toV3TableDescriptor
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest as V2AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasDeleteRequest as V2AliasDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasUpdateRequest as V2AliasUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest as V2LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest as V2LabelUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest as V2ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceDeleteRequest as V2ServiceDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest as V2ServiceUpdateRequest

import org.springframework.stereotype.Service

import reactor.core.publisher.Mono

@Service
class V3CompatService(
    private val graph: Graph,
) {
    private val tenant: String
        get() = EntityName.tenant

    // region Database CRUD (using V2 serviceDdl)

    fun getDatabase(database: String): Mono<DatabaseDescriptor> =
        graph.serviceDdl
            .getSingle(EntityName.fromOrigin(database))
            .map { it.toV3DatabaseDescriptor(tenant) }

    fun getDatabases(): Mono<List<DatabaseDescriptor>> =
        graph.serviceDdl
            .getAll(EntityName.origin)
            .map { page ->
                page.content
                    .filter { it.active }
                    .map { it.toV3DatabaseDescriptor(tenant) }
            }

    fun createDatabase(
        database: String,
        request: DatabaseCreateRequest,
    ): Mono<DatabaseDescriptor> {
        val v2Request = V2ServiceCreateRequest(desc = request.comment)
        return graph.serviceDdl
            .create(EntityName.fromOrigin(database), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3DatabaseDescriptor(tenant) }
    }

    fun updateDatabase(
        database: String,
        request: DatabaseUpdateRequest,
    ): Mono<DatabaseDescriptor> {
        val v2Request = V2ServiceUpdateRequest(
            active = request.active,
            desc = request.comment,
        )
        return graph.serviceDdl
            .update(EntityName.fromOrigin(database), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3DatabaseDescriptor(tenant) }
    }

    fun deleteDatabase(database: String): Mono<DatabaseDescriptor> =
        graph.serviceDdl
            .delete(EntityName.fromOrigin(database), V2ServiceDeleteRequest())
            .mapNotNull { it.content }
            .map { it.toV3DatabaseDescriptor(tenant) }

    // endregion

    // region Table CRUD (using V2 labelDdl)

    fun getTable(
        database: String,
        table: String,
    ): Mono<TableDescriptor.Edge> =
        graph.labelDdl
            .getSingle(EntityName(database, table))
            .map { it.toV3TableDescriptor(tenant) }

    fun getTables(database: String): Mono<List<TableDescriptor.Edge>> =
        graph.labelDdl
            .getAll(EntityName(database))
            .map { page ->
                page.content
                    .filter { it.active }
                    .map { it.toV3TableDescriptor(tenant) }
            }

    fun createTable(
        database: String,
        table: String,
        request: TableCreateRequest,
    ): Mono<TableDescriptor.Edge> {
        val v2Request = V2LabelCreateRequest(
            desc = request.comment,
            type = request.type,
            schema = request.toV2EdgeSchema(),
            dirType = request.toV2DirectionType(),
            storage = request.storage.toV2StorageString(),
            groups = request.schema.groups,
            indices = request.toV2Indices(),
            event = false,
            readOnly = false,
            mode = request.mode.toV2MutationMode(),
        )
        return graph.labelDdl
            .create(EntityName(database, table), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3TableDescriptor(tenant) }
    }

    fun updateTable(
        database: String,
        table: String,
        request: TableUpdateRequest,
    ): Mono<TableDescriptor.Edge> {
        val v2Request = V2LabelUpdateRequest(
            active = request.active,
            desc = request.comment,
            type = null,
            schema = request.toV2EdgeSchema(),
            groups = request.schema?.groups,
            indices = request.toV2Indices(),
            readOnly = null,
            mode = request.mode?.toV2MutationMode(),
        )
        return graph.labelDdl
            .update(EntityName(database, table), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3TableDescriptor(tenant) }
    }

    // endregion

    // region Alias CRUD (using V2 aliasDdl)

    fun getAlias(
        database: String,
        alias: String,
    ): Mono<AliasDescriptor> =
        graph.aliasDdl
            .getSingle(EntityName(database, alias))
            .map { it.toV3AliasDescriptor(tenant) }

    fun getAliases(database: String): Mono<List<AliasDescriptor>> =
        graph.aliasDdl
            .getAll(EntityName(database))
            .map { page ->
                page.content
                    .filter { it.active }
                    .map { it.toV3AliasDescriptor(tenant) }
            }

    fun createAlias(
        database: String,
        alias: String,
        request: AliasCreateRequest,
    ): Mono<AliasDescriptor> {
        val v2Request = V2AliasCreateRequest(
            desc = request.comment,
            target = "$database.${request.table}",
        )
        return graph.aliasDdl
            .create(EntityName(database, alias), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3AliasDescriptor(tenant) }
    }

    fun updateAlias(
        database: String,
        alias: String,
        request: AliasUpdateRequest,
    ): Mono<AliasDescriptor> {
        val v2Request = V2AliasUpdateRequest(
            active = request.active,
            desc = request.comment,
            target = request.table?.let { "$database.$it" },
        )
        return graph.aliasDdl
            .update(EntityName(database, alias), v2Request)
            .mapNotNull { it.content }
            .map { it.toV3AliasDescriptor(tenant) }
    }

    fun deleteAlias(
        database: String,
        alias: String,
    ): Mono<AliasDescriptor> =
        graph.aliasDdl
            .delete(EntityName(database, alias), V2AliasDeleteRequest())
            .mapNotNull { it.content }
            .map { it.toV3AliasDescriptor(tenant) }

    // endregion
}
