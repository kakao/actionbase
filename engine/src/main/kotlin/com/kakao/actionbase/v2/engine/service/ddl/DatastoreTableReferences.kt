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
    /** A binding whose namespace is null could not be resolved, so it matches any namespace. */
    private data class Binding(
        val namespace: String?,
        val tableName: String,
        val ref: DatastoreTableReference,
    )

    fun findAll(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> =
        bindings().map { all ->
            all
                .filter { it.tableName == tableName && (it.namespace == null || it.namespace == namespace) }
                .map { it.ref }
        }

    /** The subset that blocks a disable or drop. */
    fun findActive(
        namespace: String,
        tableName: String,
    ): Mono<List<DatastoreTableReference>> = findAll(namespace, tableName).map { refs -> refs.filter { it.active } }

    /**
     * Every binding at once, keyed by `namespace:tableName`. A single-table lookup costs the same
     * scan as this does, so anything walking a cluster should ask once here instead of per table.
     *
     * @param namespace keeps only bindings in that namespace. Unresolved bindings match any
     *   namespace, as they do in [findAll], so they survive the filter.
     */
    fun findAllByTable(namespace: String? = null): Mono<Map<String, List<DatastoreTableReference>>> =
        bindings().map { all ->
            all
                .filter { namespace == null || it.namespace == null || it.namespace == namespace }
                .groupBy({ "${it.namespace ?: defaultNamespace.orEmpty()}:${it.tableName}" }, { it.ref })
        }

    private fun bindings(): Mono<List<Binding>> =
        Mono
            .zip(storageBindings(), labelBindings())
            .map { it.t1 + it.t2 }

    private fun storageBindings(): Mono<List<Binding>> =
        graph.storageDdl
            .getAll(EntityName.origin)
            .map { page ->
                page
                    .whole("storages")
                    .content
                    .filter { it.type == StorageType.HBASE }
                    .mapNotNull { storage ->
                        storage.target()?.let { (ns, table) ->
                            Binding(ns, table, DatastoreTableReference(DatastoreTableReference.Kind.STORAGE, storage.name, storage.active))
                        }
                    }
            }

    private fun labelBindings(): Mono<List<Binding>> =
        graph.serviceDdl
            .getAll(EntityName.origin)
            .flatMapMany { services -> Flux.fromIterable(services.whole("services").content) }
            .flatMap { service ->
                val serviceName = service.name.shiftNameToService()
                graph.labelDdl.getAll(serviceName).map { it.whole("labels of $serviceName") }
            }.flatMapIterable { page -> page.content }
            .mapNotNull { label ->
                label.storage.uriTarget()?.let { (ns, table) ->
                    Binding(ns, table, DatastoreTableReference(DatastoreTableReference.Kind.LABEL, label.name, label.active))
                }
            }.collectList()

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
    private fun String.uriTarget(): Pair<String?, String>? {
        if (!EngineConstants.isSchemeUri(this)) return null
        val (ns, table) = runCatching { DatastoreUri.parse(this) }.getOrNull() ?: return null
        return (ns.ifEmpty { defaultNamespace }) to table
    }

    private fun StorageEntity.target(): Pair<String, String>? {
        val ns = conf.get("namespace")?.asText() ?: return null
        val table = conf.get("tableName")?.asText() ?: return null
        return ns to table
    }
}
