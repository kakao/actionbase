package com.kakao.actionbase.server.api.graph.v2.admin

import com.kakao.actionbase.server.service.devtools.HBaseAdminService
import com.kakao.actionbase.server.service.devtools.HBaseMetric
import com.kakao.actionbase.server.service.devtools.HBaseTableSchema
import com.kakao.actionbase.server.service.devtools.models.GraphHBaseTable
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReferences

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@Deprecated("For kc-devtools only")
@RestController
class HBaseController(
    private val graph: Graph,
    private val hBaseAdminService: HBaseAdminService,
) {
    private val references = DatastoreTableReferences(graph, defaultNamespace = null)

    @GetMapping("/graph/v2/admin/hbase/cluster")
    fun listClusters(): Mono<Map<String, List<Map<String, String>>>> =
        hBaseAdminService
            .listClusters()
            .map { mapOf("clusters" to it) }

    @GetMapping("/graph/v2/admin/hbase/cluster/{cluster}")
    fun getCluster(
        @PathVariable cluster: String,
    ): Mono<Map<String, String>> = hBaseAdminService.getCluster(cluster)

    @GetMapping("/graph/v2/admin/hbase/cluster/{cluster}/table")
    fun listTables(
        @PathVariable cluster: String,
    ): Mono<Map<String, List<GraphHBaseTable>>> =
        hBaseAdminService
            .listTables(cluster)
            .map { mapOf("tables" to it) }

    @GetMapping("/graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}")
    fun getTable(
        @PathVariable cluster: String,
        @PathVariable tableFullName: String,
    ): Mono<GraphHBaseTable> =
        hBaseAdminService
            .existTable(cluster, tableFullName)
            .flatMap { exists ->
                require(exists) { "Table $tableFullName does not exist" }
                hBaseAdminService.getTable(cluster, tableFullName)
            }

    @DeleteMapping("/graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}")
    fun deleteTable(
        @PathVariable cluster: String,
        @PathVariable tableFullName: String,
    ): Mono<Map<String, String>> =
        throwErrorIfTableInUse(tableFullName)
            .then(Mono.defer { hBaseAdminService.getTable(cluster, tableFullName) })
            .map { table ->
                require(!table.isEnabled) {
                    "Table $tableFullName is enabled, delete after disable it."
                }
            }.flatMap {
                hBaseAdminService
                    .deleteTable(cluster, tableFullName)
                    .then(Mono.just(mapOf("result" to "deleted")))
            }

    @PutMapping("/graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}")
    fun updateTable(
        @PathVariable cluster: String,
        @PathVariable tableFullName: String,
        @RequestBody request: GraphHBaseTableUpdateRequest,
    ): Mono<Map<String, String>> {
        val result =
            if (request.enable == false) {
                throwErrorIfTableInUse(tableFullName)
                    .then(Mono.defer { hBaseAdminService.disableTable(cluster, tableFullName) })
            } else {
                hBaseAdminService.enableTable(cluster, tableFullName)
            }

        return result.then(Mono.just(mapOf("result" to "updated")))
    }

    @PostMapping("/graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}")
    fun createTable(
        @PathVariable cluster: String,
        @PathVariable tableFullName: String,
        @RequestBody(required = false) request: GraphHBaseTableCreateRequest?,
    ): Mono<Map<String, String>> {
        val (namespace, tableName) = tableFullName.trim().split(":")
        return hBaseAdminService
            .createTable(
                cluster,
                namespace,
                tableName,
                request?.hBaseTableSchema ?: hBaseAdminService.hBaseTableCreator.getDefaultSchema(),
            ).then(Mono.just(mapOf("result" to "created")))
    }

    @GetMapping("/graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}/metric")
    fun getTableRegionMetric(
        @PathVariable cluster: String,
        @PathVariable tableFullName: String,
    ): Mono<Map<String, List<HBaseMetric.RegionMetric>>> =
        hBaseAdminService
            .existTable(cluster, tableFullName)
            .flatMap { exists ->
                require(exists) { "Table $tableFullName does not exist" }
                hBaseAdminService.getRegionInfo(cluster, tableFullName)
            }.map {
                mapOf("data" to it)
            }

    @GetMapping("/graph/v2/admin/hbase/cluster/{cluster}/replication")
    fun getReplicationPeers(
        @PathVariable cluster: String,
    ): Mono<Map<String, List<Map<String, Any>>>> =
        hBaseAdminService
            .getReplicationPeer(cluster)
            .map { mapOf("replication" to it) }

    // Cluster-blind, as this check has always been: it matches against this server's own metadata
    // whatever `cluster` names. For a remote cluster that errs toward refusing.
    //
    // Callers wrap the admin call in Mono.defer so nothing runs before this answers - building it
    // eagerly would open a connection, or throw on an unknown cluster, ahead of the guard.
    private fun throwErrorIfTableInUse(tableFullName: String): Mono<Void> {
        val parts = tableFullName.trim().split(":")
        require(parts.size == 2) { "Table name must be in namespace:tableName format, got $tableFullName" }
        val (namespace, tableName) = parts
        return references
            .findActive(namespace, tableName)
            .flatMap { refs ->
                if (refs.isEmpty()) {
                    Mono.empty()
                } else {
                    Mono.error(
                        IllegalArgumentException(
                            "Table $tableFullName is used by ${refs.joinToString(",") { "${it.kind}:${it.name}" }}",
                        ),
                    )
                }
            }
    }

    data class GraphHBaseTableUpdateRequest(
        val enable: Boolean?,
    )

    data class GraphHBaseTableCreateRequest(
        val columnFamilyName: String?,
        val bloomFilter: String?,
        val inMemory: Boolean?,
        val keepDeletedCells: String?,
        val dataBlockEncoding: String?,
        val compression: String?,
        val ttl: String?,
        val maxVersions: Int?,
        val minVersions: Int?,
        val blockCache: Boolean?,
        val blockSize: Int?,
        val numRegions: Int?,
    ) {
        val hBaseTableSchema: HBaseTableSchema
            get() =
                HBaseTableSchema(
                    columnFamilyName = columnFamilyName?.toByteArray() ?: "f".toByteArray(),
                    bloomFilter = bloomFilter ?: "ROW",
                    inMemory = inMemory ?: false,
                    keepDeletedCells = keepDeletedCells ?: "FALSE",
                    dataBlockEncoding = dataBlockEncoding ?: "FAST_DIFF",
                    compression = compression ?: "LZ4",
                    ttl = ttl ?: "forever",
                    maxVersions = maxVersions ?: 1,
                    minVersions = minVersions ?: 0,
                    blockCache = blockCache ?: true,
                    blockSize = blockSize ?: 65536,
                    numRegions = numRegions ?: 32,
                )
    }
}
