package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnControlRole
@EnableConfigurationProperties(MetastoreProperties::class)
class MetastoreConfiguration {
    @Bean
    fun metastoreRegistry(properties: MetastoreProperties): MetastoreRegistry = properties.toRegistry()

    @Bean
    fun metastorePurgeService(registry: MetastoreRegistry): MetastorePurgeService = MetastorePurgeService(registry)
}
