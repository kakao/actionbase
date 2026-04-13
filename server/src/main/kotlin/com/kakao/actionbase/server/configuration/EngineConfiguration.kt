package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.engine.runtime.Engine

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EngineConfiguration {
    @Bean(destroyMethod = "close")
    fun engine(properties: ServerProperties): Engine =
        Engine.create(
            metastoreReloadInitialDelay = properties.metastore.reloadInitialDelay,
            metastoreReloadInterval = properties.metastore.reloadInterval,
        )
}
