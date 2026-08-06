package com.kakao.actionbase.server.api.control.topology

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.control.topology.Env
import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.server.control.topology.TenantTopology
import com.kakao.actionbase.server.control.topology.Topology

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * What a control instance knows about the fleet. A client reads this instead of carrying its own
 * table of tenants and hostnames.
 *
 * The cluster URLs are deliberately not in the response: a client addresses the control plane, so
 * learning the hostnames behind it would only recreate the table this endpoint exists to retire.
 */
@RestController
@ConditionalOnControlRole
class TopologyController(
    private val topology: Topology,
) {
    data class TenantView(
        val tenant: String,
        val env: Env,
        val namespace: String,
        val sides: List<Side>,
    )

    @GetMapping("/control/topology")
    fun getTopology(): Mono<Map<String, List<TenantView>>> = Mono.just(mapOf("tenants" to topology.tenants.map { it.toView() }))

    @GetMapping("/control/topology/{tenant}")
    fun getTenant(
        @PathVariable tenant: String,
    ): Mono<TenantView> = Mono.just(topology.tenant(tenant).toView())

    private fun TenantTopology.toView() = TenantView(tenant, env, namespace, sides.keys.sorted())
}
