package com.kakao.actionbase.server.filter

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import java.util.concurrent.atomic.AtomicBoolean

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.BeforeEach
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

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: GET
          path: /graph/v2/service/s/label/l/edge
        - method: GET
          path: /graph/v3/databases/db/tables
        - method: POST
          path: /graph/v2/query
        - method: POST
          path: /graph/v3/query
        - method: POST
          path: /graph/v3/databases/db/tables/t/edges/get
        - method: POST
          path: /graph/v3/databases/db/tables/t/multi-edges/ids
        - method: POST
          path: /actuator/health
        """,
    )
    fun `should allow read and non-graph requests`(
        method: String,
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

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: POST
          path: /graph/v3/databases
        - method: PUT
          path: /graph/v3/databases/db/tables/t
        - method: DELETE
          path: /graph/v2/admin/service/test
        - method: PATCH
          path: /graph/v3/databases/db
        """,
    )
    fun `should block write requests on graph paths`(
        method: String,
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
        val path = "/graph/v3/databases/db/tables/t/edges"
        val exchange = buildExchange("POST", path)
        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain).block()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("POST"), "Response body should contain HTTP method")
        assertTrue(body.contains(path), "Response body should contain request path")
        assertTrue(body.contains("read-only"), "Response body should mention read-only mode")
    }

    private fun buildExchange(
        method: String,
        path: String,
    ): MockServerWebExchange {
        val request =
            when (HttpMethod.valueOf(method)) {
                HttpMethod.GET -> MockServerHttpRequest.get(path)
                HttpMethod.POST -> MockServerHttpRequest.post(path)
                HttpMethod.PUT -> MockServerHttpRequest.put(path)
                HttpMethod.DELETE -> MockServerHttpRequest.delete(path)
                HttpMethod.PATCH -> MockServerHttpRequest.patch(path)
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }
        return MockServerWebExchange.from(request)
    }
}
