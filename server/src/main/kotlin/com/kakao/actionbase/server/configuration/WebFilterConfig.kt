package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.server.filter.CustomTokenFilter
import com.kakao.actionbase.server.filter.MirrorRequestFilter
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilter
import com.kakao.actionbase.server.filter.ResponseMetaFactory
import com.kakao.actionbase.server.filter.ResponseMetaFilter
import com.kakao.actionbase.server.filter.ServerRoleRequestFilter
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

    // Before the read-only filter: what an instance serves at all comes before what it permits.
    @Bean
    @Order(1)
    fun serverRoleRequestFilter(): WebFilter = ServerRoleRequestFilter(serverProperties.role)

    @Bean
    @Order(2)
    fun readOnlyRequestFilter(): WebFilter? =
        if (serverProperties.readOnly) {
            ReadOnlyRequestFilter()
        } else {
            null
        }

    @Bean
    @Order(3)
    fun mirrorRequestFilter(): WebFilter? =
        if (properties.allowMirror) {
            MirrorRequestFilter()
        } else {
            null
        }

    @Bean("tokenAuthenticationFilter")
    @Order(4)
    fun tokenAuthenticationFilter(customTokenFilterProvider: ObjectProvider<CustomTokenFilter>): WebFilter {
        val customTokenFilter = customTokenFilterProvider.getIfAvailable()
        return TokenAuthenticationFilter(
            properties.useToken,
            properties.tokens,
            customTokenFilter,
        )
    }
}
