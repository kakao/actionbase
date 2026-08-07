package com.kakao.actionbase.server.control.cluster

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.control.topology.Topology
import com.kakao.actionbase.server.control.topology.TopologyProperties

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnControlRole
class ClusterClientConfiguration {
    @Bean
    fun clusterClient(
        topology: Topology,
        builder: WebClient.Builder,
        properties: TopologyProperties,
    ): ClusterClient = ClusterClient(topology, builder.build(), properties.requestTimeout)
}
