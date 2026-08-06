package com.kakao.actionbase.server.control.topology

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnControlRole
@EnableConfigurationProperties(TopologyProperties::class)
class TopologyConfiguration {
    @Bean
    fun topology(properties: TopologyProperties): Topology = properties.toTopology()
}
