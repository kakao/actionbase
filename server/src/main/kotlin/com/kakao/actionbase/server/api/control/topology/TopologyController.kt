package com.kakao.actionbase.server.api.control.topology

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@Configuration
@ConditionalOnControlRole
@EnableConfigurationProperties(TopologyProperties::class)
class TopologyConfiguration {
    @Bean
    fun topology(properties: TopologyProperties): Topology = properties.toTopology()
}

/**
 * What a control instance knows about the fleet. A client reads this instead of carrying its own
 * table of tenants and hostnames.
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
