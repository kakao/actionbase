package com.kakao.actionbase.server.filter

import java.util.concurrent.atomic.AtomicBoolean

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.BeforeEach
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

    @Test
    fun `should allow GET requests on graph v2 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graph/v2/service/s/label/l/edge"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should allow GET requests on graph v3 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graph/v3/databases/db/tables"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should block POST requests on graph v3 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/graph/v3/databases"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertFalse(chainCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `should block PUT requests on graph v3 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/graph/v3/databases/db/tables/t"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertFalse(chainCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `should block DELETE requests on graph v2 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.delete("/graph/v2/admin/service/test"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertFalse(chainCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `should block PATCH requests on graph v3 paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.patch("/graph/v3/databases/db"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertFalse(chainCalled.get())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `should allow POST requests on non-graph paths`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/actuator/health"))
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should allow POST to edges-get endpoint (read-only query)`() {
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.post("/graph/v3/databases/db/tables/t/edges/get"),
            )
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should allow POST to multi-edges-ids endpoint (read-only query)`() {
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.post("/graph/v3/databases/db/tables/t/multi-edges/ids"),
            )
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should allow POST to v2 query endpoint (read-only query)`() {
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.post("/graph/v2/query"),
            )
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should allow POST to v3 query endpoint (read-only query)`() {
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.post("/graph/v3/query"),
            )
        val chainCalled = AtomicBoolean(false)
        val chain =
            WebFilterChain {
                chainCalled.set(true)
                Mono.empty()
            }

        filter.filter(exchange, chain).block()

        assertTrue(chainCalled.get())
    }

    @Test
    fun `should include method and path in error response body`() {
        val path = "/graph/v3/databases/db/tables/t/edges"
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path))
        val chain = WebFilterChain { Mono.empty() }

        filter.filter(exchange, chain).block()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("POST"), "Response body should contain HTTP method")
        assertTrue(body.contains(path), "Response body should contain request path")
        assertTrue(body.contains("read-only"), "Response body should mention read-only mode")
    }
}
