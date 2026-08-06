package com.kakao.actionbase.server.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

class TokenAuthenticationFilterTest {
    private val token = "secret"

    @Test
    fun `the operational apis need a token`() {
        assertRejected("/control/topology")
        assertRejected("/control/topology/kc")
    }

    @Test
    fun `the data plane needs a token`() {
        assertRejected("/graph/v2/service")
        assertRejected("/graph/v3/databases")
    }

    @Test
    fun `a valid token reaches the operational apis`() {
        assertAllowed("/control/topology", token)
    }

    // Probes must answer unauthenticated or every pod fails readiness.
    @Test
    fun `probes need no token`() {
        assertAllowed("/graph/health")
        assertAllowed("/graph/health/liveness")
        assertAllowed("/")
    }

    @Test
    fun `nothing is protected when tokens are off`() {
        assertAllowed("/control/topology", useToken = false)
        assertAllowed("/graph/v3/databases", useToken = false)
    }

    private fun assertAllowed(
        path: String,
        authHeader: String? = null,
        useToken: Boolean = true,
    ) {
        val exchange = buildExchange(path, authHeader)
        var reached = false
        filter(useToken)
            .filter(
                exchange,
                WebFilterChain {
                    reached = true
                    Mono.empty()
                },
            ).block()
        assertTrue(reached, "Expected $path to be allowed")
    }

    private fun assertRejected(path: String) {
        val exchange = buildExchange(path, authHeader = null)
        var reached = false
        filter(useToken = true)
            .filter(
                exchange,
                WebFilterChain {
                    reached = true
                    Mono.empty()
                },
            ).block()
        assertFalse(reached, "Expected $path to require a token")
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    private fun filter(useToken: Boolean) = TokenAuthenticationFilter(useToken, setOf(token))

    private fun buildExchange(
        path: String,
        authHeader: String?,
    ): MockServerWebExchange {
        val request = MockServerHttpRequest.method(HttpMethod.GET, path)
        authHeader?.let { request.header("Authorization", it) }
        return MockServerWebExchange.from(request)
    }
}
