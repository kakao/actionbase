package com.kakao.actionbase.server.api.graph.v3.datastore.hbase

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableInfo
import com.kakao.actionbase.server.auth.ActorRole
import com.kakao.actionbase.server.configuration.ConditionalOnHBaseDatastore
import com.kakao.actionbase.server.configuration.GraphProperties
import com.kakao.actionbase.server.configuration.HttpHeaderConstants

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 * Table CRUD access control summary
 *
 * | Operation  | Production Default | Non-Production Default | role = admin |
 * |------------|-------------------|----------------------|--------------|
 * | Read       | O                 | O                    | O            |
 * | Create     | O                 | O                    | O            |
 * | Update     | X                 | O                    | O            |
 * | Delete     | X                 | X                    | O            |
 *
 * Admin role information is injected through headers (Actor-ROLE: ADMIN)
 */
@RestController
@ConditionalOnHBaseDatastore
class DatastoreHBaseController(
    private val graphProperties: GraphProperties,
    private val service: DatastoreHBaseService,
) {
    @GetMapping("/graph/v3/datastore/hbase/namespaces")
    fun getNamespaces(): Mono<List<String>> = service.getNamespaces().toMono()

    @GetMapping("/graph/v3/datastore/hbase/tables")
    fun listTables(): Mono<Map<String, List<HBaseTableInfo>>> {
        // Extract namespaces from all storages to create a distinct namespace list
        return service
            .getTables()
            .map { mapOf("tables" to it) }
    }

    @GetMapping("/graph/v3/datastore/hbase/tables/{tableName}")
    fun getTable(
        @PathVariable tableName: String,
    ): Mono<HBaseTableInfo> = service.getTable(tableName)

    // New tables use tenant-format namespace instead of 'kc_graph'
    @PostMapping("/graph/v3/datastore/hbase/tables/{tableName}")
    fun createTable(
        @PathVariable tableName: String,
        @RequestBody(required = false) request: HBaseTableCreateRequest?,
    ): Mono<Map<String, String>> =
        service
            .createTable(tableName, request)
            .then(Mono.just(mapOf("result" to "created")))

    @PutMapping("/graph/v3/datastore/hbase/tables/{tableName}")
    fun updateTable(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
        @RequestBody request: HBaseTableUpdateRequest,
    ): Mono<Map<String, String>> {
        requireProductionAdmin(actorRole, "actionbase does not support HBase update operations")
        return service
            .updateTable(tableName, request)
            .then(Mono.just(mapOf("result" to "updated")))
    }

    @PostMapping("/graph/v3/datastore/hbase/tables/{tableName}/enable")
    fun enableTable(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
    ): Mono<Map<String, String>> {
        requireProductionAdmin(actorRole, "actionbase does not support HBase enable operations")
        return service
            .enableTable(tableName)
            .then(Mono.just(mapOf("result" to "enabled")))
    }

    @PostMapping("/graph/v3/datastore/hbase/tables/{tableName}/disable")
    fun disableTable(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
    ): Mono<Map<String, String>> {
        requireProductionAdmin(actorRole, "actionbase does not support HBase disable operations")
        return service
            .disableTable(tableName)
            .then(Mono.just(mapOf("result" to "disabled")))
    }

    @PostMapping("/graph/v3/datastore/hbase/tables/{tableName}/replication/enable")
    fun enableReplication(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
    ): Mono<Map<String, String>> {
        requireProductionAdmin(actorRole, "actionbase does not support HBase replication enable operations")
        return service
            .enableReplication(tableName)
            .then(Mono.just(mapOf("result" to "replication-enabled")))
    }

    @PostMapping("/graph/v3/datastore/hbase/tables/{tableName}/replication/disable")
    fun disableReplication(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
    ): Mono<Map<String, String>> {
        requireProductionAdmin(actorRole, "actionbase does not support HBase replication disable operations")
        return service
            .disableReplication(tableName)
            .then(Mono.just(mapOf("result" to "replication-disabled")))
    }

    @DeleteMapping("/graph/v3/datastore/hbase/tables/{tableName}")
    fun deleteTable(
        @ModelAttribute actorRole: ActorRole,
        @PathVariable tableName: String,
    ): Mono<Map<String, String>> {
        requireAdmin(actorRole, "actionbase does not support HBase delete operations")
        return service
            .deleteTable(tableName)
            .then(Mono.just(mapOf("result" to "deleted")))
    }

    @GetMapping("/graph/v3/datastore/hbase/tables/{tableName}/metric")
    fun getTableMetricSummary(
        @PathVariable tableName: String,
    ): Mono<Map<String, Any>> =
        service
            .getTableMetricSummary(tableName)

    @ModelAttribute("actorRole")
    fun populateActorRole(
        @RequestHeader(value = HttpHeaderConstants.ACTOR_ROLE, required = false) actorRole: ActorRole?,
    ): ActorRole = actorRole ?: ActorRole.UNKNOWN

    /**
     * Requires ADMIN role only in production environment
     * Allows all roles in non-production environments
     */
    private fun requireProductionAdmin(
        actorRole: ActorRole,
        errorMessage: String,
    ) {
        if (graphProperties.production && actorRole != ActorRole.ADMIN) {
            throw UnsupportedOperationException(errorMessage)
        }
    }

    /**
     * Requires ADMIN role in all environments
     */
    private fun requireAdmin(
        actorRole: ActorRole,
        errorMessage: String,
    ) {
        if (actorRole != ActorRole.ADMIN) {
            throw UnsupportedOperationException(errorMessage)
        }
    }
}
