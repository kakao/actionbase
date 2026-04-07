package com.kakao.actionbase.server.filter

import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

// Rejects non-GET methods on graph path prefixes with 403.
// Exceptions: read-only POST endpoints matched by readSuffixes.
class ReadOnlyRequestFilter : WebFilter {
    private val log = LoggerFactory.getLogger(ReadOnlyRequestFilter::class.java)

    private val paths = setOf("/graph/v2", "/graph/v3")
    private val readMethod = HttpMethod.GET
    private val readSuffixes =
        setOf(
            "/edges/get",
            "/multi-edges/ids",
            "/query",
        )

    init {
        log.info("ReadOnlyRequestFilter is active. Write operations on {} will be rejected.", paths)
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val method = exchange.request.method
        val path = exchange.request.uri.path

        if (method == readMethod || !paths.any { path.startsWith(it) } || isRead(path)) {
            return chain.filter(exchange)
        }

        log.warn("Blocked write request in read-only mode: {} {}", method, path)
        val bufferFactory = exchange.response.bufferFactory()
        val messageBuffer =
            bufferFactory.wrap(
                """{"message":"Write operation not allowed in read-only mode: $method $path"}""".toByteArray(),
            )
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        return exchange.response.writeWith(Mono.just(messageBuffer))
    }

    private fun isRead(path: String): Boolean = readSuffixes.any { path.endsWith(it) }
}
