package com.kakao.actionbase.server.filter

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

class TokenAuthenticationFilter(
    private val useToken: Boolean,
    private val validTokens: Set<String>,
    private val customTokenFilter: CustomTokenFilter? = null,
) : WebFilter {
    private val log = LoggerFactory.getLogger(TokenAuthenticationFilter::class.java)

    // Tokens cover the operational apis too, not just the data plane they reach. QUEUE is left out
    // as it always has been - adding it would start rejecting clients that send no token.
    private val protectedPaths = PathPrefixes.GRAPH + PathPrefixes.CONTROL

    init {
        log.info("TokenAuthenticationFilter is added. useToken: $useToken, protectedPaths: $protectedPaths")
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        if (!useToken) {
            return chain.filter(exchange)
        }

        val requestPath = exchange.request.uri.path

        return if (protectedPaths.any { requestPath.startsWith(it) }) {
            val authHeader = exchange.request.headers.getFirst("Authorization")
            if (validTokens.contains(authHeader)) {
                chain.filter(exchange) // Token is valid, proceed with the request
            } else if (authHeader != null && customTokenFilter?.isValid(authHeader) == true) {
                chain.filter(exchange)
            } else {
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                Mono.empty() // Unauthorized access
            }
        } else {
            chain.filter(exchange) // Not a protected path, proceed without token validation
        }
    }

    fun warmUp() {
        customTokenFilter?.warmUp()
    }
}
