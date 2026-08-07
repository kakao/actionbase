package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableInfo
import com.kakao.actionbase.server.control.cluster.ClusterClient
import com.kakao.actionbase.server.control.cluster.SideResponse
import com.kakao.actionbase.server.control.topology.Env
import com.kakao.actionbase.server.control.topology.Topology
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReference

import org.springframework.core.ParameterizedTypeReference

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Collects what every selected tenant's clusters hold and hands it to [CleanupProjection].
 *
 * Both sides of a tenant are always read: a cleanup view whose standby is missing would hide
 * exactly the work that is left to do.
 */
class CleanupService(
    private val topology: Topology,
    private val clusterClient: ClusterClient,
) {
    fun htables(
        tenant: String?,
        env: Env?,
        headers: Map<String, String>,
    ): Mono<HtablesView> =
        Flux
            .fromIterable(selected(tenant, env))
            .flatMapSequential { snapshots(it, headers) }
            .collectList()
            .map { CleanupProjection.project(it.flatten()) }

    /** A named tenant that is not configured is a bad request, not an empty answer. */
    private fun selected(
        tenant: String?,
        env: Env?,
    ): List<String> {
        tenant?.let { topology.tenant(it) }
        return topology.tenants
            .filter { tenant == null || it.tenant == tenant }
            .filter { env == null || it.env == env }
            .map { it.tenant }
    }

    private fun snapshots(
        tenant: String,
        headers: Map<String, String>,
    ): Mono<List<SideSnapshot>> =
        Mono
            .zip(
                clusterClient.get(tenant, TABLES_PATH, fanout = true, TABLES, headers),
                clusterClient.get(tenant, REFERENCES_PATH, fanout = true, REFERENCES, headers),
            ).map { answers -> merge(tenant, answers.t1, answers.t2) }

    private fun merge(
        tenant: String,
        tables: List<SideResponse<Map<String, List<HBaseTableInfo>>>>,
        references: List<SideResponse<Map<String, Map<String, List<DatastoreTableReference>>>>>,
    ): List<SideSnapshot> {
        val referencesBySide = references.associateBy { it.side }
        return tables.map { table ->
            val reference = referencesBySide[table.side]
            SideSnapshot(
                tenant = tenant,
                side = table.side,
                cluster = topology.cluster(tenant, table.side),
                tables =
                    table.body
                        ?.get("tables")
                        .orEmpty()
                        .map { it.toTableInfo() },
                references = reference?.body?.get("references").orEmpty(),
                // Either call failing leaves this side's picture incomplete, so it is a failed side
                // rather than a half-answered one that reads as "nothing left to clean up".
                error = table.error ?: reference?.error,
            )
        }
    }

    private fun HBaseTableInfo.toTableInfo() =
        SideSnapshot.TableInfo(
            name = name,
            enabled = isEnabled,
            replicationScope = replicationScope,
        )

    companion object {
        private const val TABLES_PATH = "/graph/v3/datastore/hbase/tables"
        private const val REFERENCES_PATH = "/graph/v3/datastore/hbase/references"

        private val TABLES = object : ParameterizedTypeReference<Map<String, List<HBaseTableInfo>>>() {}

        private val REFERENCES = object : ParameterizedTypeReference<Map<String, Map<String, List<DatastoreTableReference>>>>() {}
    }
}
