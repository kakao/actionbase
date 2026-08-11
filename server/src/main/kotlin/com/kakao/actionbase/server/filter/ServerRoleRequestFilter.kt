package com.kakao.actionbase.server.filter

import com.kakao.actionbase.server.configuration.ServerRole

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

/**
 * DATA refuses /control, CONTROL refuses the data plane.
 *
 * Denies by data prefix rather than allowing only /control, so health under /graph keeps answering.
 * Gates the http surface only - a control instance still opens the datastore.
 */
class ServerRoleRequestFilter(
    private val role: ServerRole,
) : WebFilter {
    private val log = LoggerFactory.getLogger(ServerRoleRequestFilter::class.java)

    private val refused: Set<String> =
        when (role) {
            ServerRole.DATA -> PathPrefixes.CONTROL
            ServerRole.CONTROL -> PathPrefixes.DATA
        }

    init {
        log.info("ServerRoleRequestFilter is active. role={}, requests on {} will be refused.", role, refused)
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val path = exchange.request.uri.path

        if (refused.none { path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val method = exchange.request.method
        log.warn("Refused request outside the {} role's surface: {} {}", role, method, path)
        val messageBuffer =
            exchange.response.bufferFactory().wrap(
                """{"message":"Not served by a $role instance: $method $path"}""".toByteArray(),
            )
        exchange.response.statusCode = HttpStatus.NOT_FOUND
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        return exchange.response.writeWith(Mono.just(messageBuffer))
    }
}
