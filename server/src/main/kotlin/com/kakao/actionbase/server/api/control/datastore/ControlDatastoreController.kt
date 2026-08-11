package com.kakao.actionbase.server.api.control.datastore

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableInfo
import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.configuration.HttpHeaderConstants
import com.kakao.actionbase.server.control.cluster.CallerHeaders
import com.kakao.actionbase.server.control.cluster.ClusterClient
import com.kakao.actionbase.server.control.cluster.SideResponse
import com.kakao.actionbase.server.control.topology.Side

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * What each of a tenant's clusters holds, in one answer. A client asks the control plane once
 * instead of addressing every cluster itself.
 */
@RestController
@ConditionalOnControlRole
class ControlDatastoreController(
    private val clusterClient: ClusterClient,
) {
    data class TablesView(
        val tenant: String,
        val sides: List<SideView>,
    )

    data class SideView(
        val side: Side,
        val ok: Boolean,
        val tables: List<HBaseTableInfo>? = null,
        val error: String? = null,
    )

    @GetMapping("/control/tenants/{tenant}/datastore/tables")
    fun listTables(
        @PathVariable tenant: String,
        @RequestParam(required = false, defaultValue = "false") fanout: Boolean,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestHeader(value = HttpHeaderConstants.ACTOR_ROLE, required = false) actorRole: String?,
    ): Mono<TablesView> =
        clusterClient
            .get(tenant, TABLES_PATH, fanout, TABLES, CallerHeaders.forwarded(authorization, actorRole))
            .map { sides -> TablesView(tenant, sides.map { it.toView() }) }

    private fun SideResponse<Map<String, List<HBaseTableInfo>>>.toView() =
        SideView(
            side = side,
            ok = ok,
            tables = body?.get("tables"),
            error = error,
        )

    companion object {
        private const val TABLES_PATH = "/graph/v3/datastore/hbase/tables"

        private val TABLES = object : ParameterizedTypeReference<Map<String, List<HBaseTableInfo>>>() {}
    }
}
