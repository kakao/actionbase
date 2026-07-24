package com.kakao.actionbase.server.filter

import org.springframework.http.HttpStatus
import org.springframework.http.server.PathContainer
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser

import reactor.core.publisher.Mono

class MirrorRequestFilter : WebFilter {
    private val prefix = "/mirror/"

    private val pathPatternParser = PathPatternParser()
    private val allowedPathPattern =
        listOf(
            pathPatternParser.parse("/graph/v2/service/{service}/label/{label}/edge"),
        )

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val requestPath = exchange.request.uri.path

        if (!requestPath.startsWith(prefix)) {
            return chain.filter(exchange)
        }

        val suffix = requestPath.substring(prefix.length)
        val mirroredFrom = suffix.substringBefore('/')
        val prefixToReplace = "$prefix$mirroredFrom"
        val newPath = requestPath.removePrefix(prefixToReplace)

        // This should be replaced by ISSUE-2074.
        return if (isAllowed(newPath)) {
            val mutatedRequest =
                exchange.request
                    .mutate()
                    .path(newPath)
                    .build()
            val mutatedExchange = exchange.mutate().request(mutatedRequest).build()
            chain.filter(mutatedExchange)
        } else {
            val method = exchange.request.method
            val bufferFactory = exchange.response.bufferFactory()
            val messageBuffer =
                bufferFactory.wrap(
                    """{"message": "Mirrored request is not allowed: $method $requestPath"}""".toByteArray(),
                )
            exchange.response.statusCode = HttpStatus.NOT_ACCEPTABLE
            exchange.response.writeWith(Mono.just(messageBuffer))
        }
    }

    private fun isAllowed(path: String): Boolean {
        val pathContainer = PathContainer.parsePath(path)
        return allowedPathPattern.any { it.matches(pathContainer) }
    }
}
