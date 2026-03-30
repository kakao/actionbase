package com.kakao.actionbase.server.filter

import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

class ReadOnlyRequestFilterTest {
    private lateinit var filter: ReadOnlyRequestFilter

    @BeforeEach
    fun setup() {
        filter = ReadOnlyRequestFilter()
    }

    @ParameterizedTest(name = "{0} {1} -> allowed")
    @MethodSource("readPaths")
    fun `should allow read and non-graph requests`(
        method: HttpMethod,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get(), "Expected $method $path to be allowed")
    }

    @ParameterizedTest(name = "{0} {1} -> blocked")
    @MethodSource("writePaths")
    fun `should block write requests on graph paths`(
        method: HttpMethod,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertFalse(chainCalled.get(), "Expected $method $path to be blocked")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `should include method and path in error response body`() {
        val path = WRITE_PATHS[0].second
        val exchange = buildExchange(HttpMethod.POST, path)
        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain).block()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("POST"), "Response body should contain HTTP method")
        assertTrue(body.contains(path), "Response body should contain request path")
        assertTrue(body.contains("read-only"), "Response body should mention read-only mode")
    }

    private fun buildExchange(
        method: HttpMethod,
        path: String,
    ): MockServerWebExchange {
        val request =
            when (method) {
                HttpMethod.GET -> MockServerHttpRequest.get(path)
                HttpMethod.POST -> MockServerHttpRequest.post(path)
                HttpMethod.PUT -> MockServerHttpRequest.put(path)
                HttpMethod.DELETE -> MockServerHttpRequest.delete(path)
                HttpMethod.PATCH -> MockServerHttpRequest.patch(path)
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }
        return MockServerWebExchange.from(request)
    }

    companion object {
        val READ_PATHS =
            listOf(
                HttpMethod.GET to "/graph/v2/service/s/label/l/edge",
                HttpMethod.GET to "/graph/v3/databases/db/tables",
                HttpMethod.POST to "/graph/v2/query",
                HttpMethod.POST to "/graph/v3/query",
                HttpMethod.POST to "/graph/v3/databases/db/tables/t/edges/get",
                HttpMethod.POST to "/graph/v3/databases/db/tables/t/multi-edges/ids",
                HttpMethod.POST to "/actuator/health",
            )

        val WRITE_PATHS =
            listOf(
                HttpMethod.POST to "/graph/v3/databases",
                HttpMethod.PUT to "/graph/v3/databases/db/tables/t",
                HttpMethod.DELETE to "/graph/v2/admin/service/test",
                HttpMethod.PATCH to "/graph/v3/databases/db",
            )

        @JvmStatic
        fun readPaths(): Stream<Arguments> = READ_PATHS.map { Arguments.of(it.first, it.second) }.stream()

        @JvmStatic
        fun writePaths(): Stream<Arguments> = WRITE_PATHS.map { Arguments.of(it.first, it.second) }.stream()
    }
}
