package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.server.filter.CustomTokenFilter
import com.kakao.actionbase.server.filter.MirrorRequestFilter
import com.kakao.actionbase.server.filter.ResponseMetaFactory
import com.kakao.actionbase.server.filter.ResponseMetaFilter
import com.kakao.actionbase.server.filter.TokenAuthenticationFilter

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.info.GitProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.web.server.WebFilter

@Configuration
class WebFilterConfig(
    private val properties: GraphProperties,
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
    fun mirrorRequestFilter(): WebFilter? =
        if (properties.allowMirror) {
            MirrorRequestFilter()
        } else {
            null
        }

    @Bean("tokenAuthenticationFilter")
    @Order(2)
    fun tokenAuthenticationFilter(customTokenFilterProvider: ObjectProvider<CustomTokenFilter>): WebFilter {
        val customTokenFilter = customTokenFilterProvider.getIfAvailable()
        return TokenAuthenticationFilter(
            properties.useToken,
            properties.tokens,
            customTokenFilter,
        )
    }
}
