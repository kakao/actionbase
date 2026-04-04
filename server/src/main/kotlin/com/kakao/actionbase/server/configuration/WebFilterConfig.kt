package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.server.filter.CustomTokenFilter
import com.kakao.actionbase.server.filter.MirrorRequestFilter
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilter
import com.kakao.actionbase.server.filter.ResponseMetaFactory
import com.kakao.actionbase.server.filter.ResponseMetaFilter
import com.kakao.actionbase.server.filter.TokenAuthenticationFilter

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.web.server.WebFilter

@Configuration
class WebFilterConfig(
    private val properties: GraphProperties,
    private val serverProperties: ServerProperties,
    private val gitProperties: GitProperties?,
    private val buildProperties: BuildProperties,
) {
    @Bean("responseMetaFilter")
    @Order(0)
    fun responseMetaFilter(): WebFilter =
        ResponseMetaFilter(
            ResponseMetaFactory(gitProperties, buildProperties),
        )

    @Bean
    @Order(1)
    fun readOnlyRequestFilter(): ReadOnlyRequestFilter? =
        if (serverProperties.readOnly) {
            ReadOnlyRequestFilter()
        } else {
            null
        }

    @Bean
    @Order(2)
    fun mirrorRequestFilter(): WebFilter? =
        if (properties.allowMirror) {
            MirrorRequestFilter()
        } else {
            null
        }

    @Bean("tokenAuthenticationFilter")
    @Order(3)
    fun tokenAuthenticationFilter(customTokenFilterProvider: ObjectProvider<CustomTokenFilter>): WebFilter {
        val customTokenFilter = customTokenFilterProvider.getIfAvailable()
        return TokenAuthenticationFilter(
            properties.useToken,
            properties.tokens,
            customTokenFilter,
        )
    }

    // Activate ReadOnlyRequestFilter after ApplicationReadyEvent when warmup is disabled.
    // When warmup is enabled, ServiceLabelEdgeControllerWarmUp activates it after warmup completes.
    @Bean
    @ConditionalOnProperty(name = ["kc.graph.warmup.enabled"], havingValue = "false", matchIfMissing = true)
    fun readOnlyFilterActivator(filterProvider: ObjectProvider<ReadOnlyRequestFilter>): ApplicationListener<ApplicationReadyEvent> =
        ApplicationListener {
            filterProvider.ifAvailable { it.activate() }
        }
}
