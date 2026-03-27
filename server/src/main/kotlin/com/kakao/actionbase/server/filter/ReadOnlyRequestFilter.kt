package com.kakao.actionbase.server.filter

import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

class ReadOnlyRequestFilter : WebFilter {
    private val log = LoggerFactory.getLogger(ReadOnlyRequestFilter::class.java)

    private val protectedPaths = setOf("/graph/v2", "/graph/v3")
    private val mutatingMethods = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)

    // POST endpoints that are read-only queries (use POST for complex request bodies)
    private val readOnlyPostSuffixes =
        setOf(
            "/edges/get",
            "/multi-edges/ids",
            "/query",
        )

    init {
        log.info("ReadOnlyRequestFilter is active. Write operations on {} will be rejected.", protectedPaths)
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val method = exchange.request.method
        val path = exchange.request.uri.path

        if (method in mutatingMethods && protectedPaths.any { path.startsWith(it) }) {
            if (method == HttpMethod.POST && isReadOnlyPost(path)) {
                return chain.filter(exchange)
            }
            log.warn("Blocked write request in read-only mode: {} {}", method, path)
            val bufferFactory = exchange.response.bufferFactory()
            val messageBuffer =
                bufferFactory.wrap(
                    """{"message":"Write operation not allowed in read-only mode: $method $path"}""".toByteArray(),
                )
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return exchange.response.writeWith(Mono.just(messageBuffer))
        }

        return chain.filter(exchange)
    }

    private fun isReadOnlyPost(path: String): Boolean =
        readOnlyPostSuffixes.any { path.endsWith(it) }
}
