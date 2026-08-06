package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.engine.EngineConstants
import com.kakao.actionbase.engine.storage.DatastoreUri
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.StorageType

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class DatastoreTableReference(
    val kind: Kind,
    val name: EntityName,
    val active: Boolean,
) {
    enum class Kind {
        STORAGE,
        LABEL,
    }
}

/**
 * The referential-integrity rule for the last hop of `alias -> label -> storage -> datastore table`.
 * The hops above it are enforced by [LabelDdlService.canDeactivate] and
 * [StorageDdlService.canDeactivate].
 *
 * @param defaultNamespace fills in for a URI that omits it (`datastore:///{table}`). Null where the
 *   caller spans clusters; such a URI then matches on table name alone, over-reporting rather than
 *   letting an irreversible drop through.
 */
class DatastoreTableReferences(
    private val graph: Graph,
    private val defaultNamespace: String?,
) {
    fun findAll(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> =
        Mono
            .zip(storageRefs(namespace, tableName), labelRefs(namespace, tableName))
            .map { it.t1 + it.t2 }

    /** The subset that blocks a disable or drop. */
    fun findActive(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> = findAll(namespace, tableName).map { refs -> refs.filter { it.active } }

    private fun storageRefs(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> =
        graph.storageDdl
            .getAll(EntityName.origin)
            .map { page ->
                page
                    .whole("storages")
                    .content
                    .filter { it.type == StorageType.HBASE && it.namesTable(namespace, tableName) }
                    .map { DatastoreTableReference(DatastoreTableReference.Kind.STORAGE, it.name, it.active) }
            }

    private fun labelRefs(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> =
        graph.serviceDdl
            .getAll(EntityName.origin)
            .flatMapMany { services -> Flux.fromIterable(services.whole("services").content) }
            .flatMap { service ->
                val serviceName = service.name.shiftNameToService()
                graph.labelDdl.getAll(serviceName).map { it.whole("labels of $serviceName") }
            }.flatMapIterable { page -> page.content }
            .filter { label -> label.storage.uriNames(namespace, tableName) }
            .map { label -> DatastoreTableReference(DatastoreTableReference.Kind.LABEL, label.name, label.active) }
            .collectList()

    /**
     * A metadata scan stops at `metadataFetchLimit` without saying so, and a truncated page cannot
     * prove that nothing references the table. Refuse rather than report an absence we can't back -
     * the caller is deciding whether to drop.
     */
    private fun <T> DdlPage<T>.whole(what: String): DdlPage<T> {
        check(content.size < graph.metadataFetchLimit) {
            "Cannot resolve datastore table references: the scan of $what hit the " +
                "metadataFetchLimit of ${graph.metadataFetchLimit}, so the result may be incomplete"
        }
        return this
    }

    // A malformed URI is skipped rather than raised: one label's bad metadata must not make an
    // unrelated table unlistable.
    private fun String.uriNames(
        namespace: String,
        tableName: String,
    ): Boolean {
        if (!EngineConstants.isSchemeUri(this)) return false
        val (ns, table) = runCatching { DatastoreUri.parse(this) }.getOrNull() ?: return false
        if (table != tableName) return false
        if (ns.isNotEmpty()) return ns == namespace
        // Namespace omitted and no default to resolve it with, so the table name is all we can match on.
        return defaultNamespace == null || defaultNamespace == namespace
    }

    private fun StorageEntity.namesTable(
        namespace: String,
        tableName: String,
    ): Boolean = conf.get("namespace")?.asText() == namespace && conf.get("tableName")?.asText() == tableName
}
