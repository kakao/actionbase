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
        - path: /graph/v2/service/s/label/l/edge
        - path: /graph/v3/databases/db/tables
        - path: /graph/v3/databases/db/tables/t/edges/scan/ts_desc
        - path: /graph/v3/databases/db/tables/t/edges/count
        - path: /graph/v3/databases/db/tables/t/edges/counts
        - path: /graph/v3/databases/db/tables/t/edges/agg/group
        """,
    )
    fun `should allow GET requests on graph paths`(path: String) {
        val exchange = buildExchange("GET", path)
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get(), "Expected GET $path to be allowed")
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - path: /graph/v2/query
        - path: /graph/v3/query
        - path: /graph/v3/databases/db/tables/t/edges/get
        - path: /graph/v3/databases/db/tables/t/multi-edges/ids
        - path: /actuator/health
        """,
    )
    fun `should allow read-only POST requests`(path: String) {
        val exchange = buildExchange("POST", path)
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get(), "Expected POST $path to be allowed")
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: POST
          path: /graph/v3/databases
        - method: POST
          path: /graph/v3/databases/db/tables/t/edges
        - method: PUT
          path: /graph/v3/databases/db/tables/t
        - method: DELETE
          path: /graph/v2/admin/service/test
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
        val path = WRITE_PATHS.first()
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
                else -> throw IllegalArgumentException("Unsupported method: $method")
            }
        return MockServerWebExchange.from(request)
    }

    companion object {
        val READ_PATHS =
            listOf(
                "/graph/v2/service/s/label/l/edge",
                "/graph/v3/databases/db/tables",
                "/graph/v3/databases/db/tables/t/edges/scan/ts_desc",
                "/graph/v3/databases/db/tables/t/edges/count",
                "/graph/v3/databases/db/tables/t/edges/counts",
                "/graph/v3/databases/db/tables/t/edges/agg/group",
                "/graph/v2/query",
                "/graph/v3/query",
                "/graph/v3/databases/db/tables/t/edges/get",
                "/graph/v3/databases/db/tables/t/multi-edges/ids",
            )

        val WRITE_PATHS =
            listOf(
                "/graph/v3/databases",
                "/graph/v3/databases/db/tables/t",
                "/graph/v3/databases/db/tables/t/edges",
                "/graph/v2/admin/service/test",
            )
    }
}
