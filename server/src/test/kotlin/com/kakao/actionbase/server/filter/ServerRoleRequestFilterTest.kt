package com.kakao.actionbase.server.filter

import com.kakao.actionbase.server.configuration.ServerRole
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilterTest.Companion.CONTROL_ENDPOINTS
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilterTest.Companion.CONTROL_WRITE_ENDPOINTS
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilterTest.Companion.NON_GRAPH_ENDPOINTS
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilterTest.Companion.READ_ENDPOINTS
import com.kakao.actionbase.server.filter.ReadOnlyRequestFilterTest.Companion.WRITE_ENDPOINTS

import java.util.stream.Stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain

import reactor.core.publisher.Mono

// Reuses ReadOnlyRequestFilterTest's classification, so its exhaustiveness check covers this filter.
class ServerRoleRequestFilterTest {
    @ParameterizedTest
    @MethodSource("dataEndpoints")
    fun `a data instance serves the data plane`(
        method: String,
        path: String,
    ) {
        assertAllowed(ServerRole.DATA, method, path)
    }

    @ParameterizedTest
    @MethodSource("controlEndpoints")
    fun `a data instance refuses the operational apis`(
        method: String,
        path: String,
    ) {
        assertRefused(ServerRole.DATA, method, path)
    }

    @ParameterizedTest
    @MethodSource("dataEndpoints")
    fun `a control instance refuses the data plane, reads included`(
        method: String,
        path: String,
    ) {
        assertRefused(ServerRole.CONTROL, method, path)
    }

    @ParameterizedTest
    @MethodSource("controlEndpoints")
    fun `a control instance serves the operational apis`(
        method: String,
        path: String,
    ) {
        assertAllowed(ServerRole.CONTROL, method, path)
    }

    // Health lives under /graph, which is why the filter denies by prefix instead of allowlisting.
    @ParameterizedTest
    @MethodSource("neutralEndpoints")
    fun `probes and actuator survive either role`(
        method: String,
        path: String,
    ) {
        assertAllowed(ServerRole.DATA, method, path)
        assertAllowed(ServerRole.CONTROL, method, path)
    }

    @Test
    fun `actuator is reachable on a control instance`() {
        assertAllowed(ServerRole.CONTROL, "GET", "/actuator/health")
        assertAllowed(ServerRole.CONTROL, "GET", "/actuator/info")
    }

    @Test
    fun `the refusal names the role, method and path`() {
        val exchange = buildExchange("POST", "/graph/v3/databases")
        ServerRoleRequestFilter(ServerRole.CONTROL).filter(exchange, WebFilterChain { Mono.empty() }).block()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("CONTROL"), body)
        assertTrue(body.contains("POST"), body)
        assertTrue(body.contains("/graph/v3/databases"), body)
    }

    private fun assertAllowed(
        role: ServerRole,
        method: String,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        var passed = false
        ServerRoleRequestFilter(role)
            .filter(
                exchange,
                WebFilterChain {
                    passed = true
                    Mono.empty()
                },
            ).block()
        assertTrue(passed, "Expected $method $path to be allowed on a $role instance")
    }

    private fun assertRefused(
        role: ServerRole,
        method: String,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        var passed = false
        ServerRoleRequestFilter(role)
            .filter(
                exchange,
                WebFilterChain {
                    passed = true
                    Mono.empty()
                },
            ).block()
        assertFalse(passed, "Expected $method $path to be refused on a $role instance")
        assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
        assertEquals(MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
    }

    private fun buildExchange(
        method: String,
        path: String,
    ): MockServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path))

    companion object {
        @JvmStatic
        fun dataEndpoints(): Stream<Arguments> = (READ_ENDPOINTS + WRITE_ENDPOINTS).sorted().map { ReadOnlyRequestFilterTest.toTestArgs(it) }.stream()

        @JvmStatic
        fun controlEndpoints(): Stream<Arguments> =
            (CONTROL_ENDPOINTS + CONTROL_WRITE_ENDPOINTS)
                .sorted()
                .map { ReadOnlyRequestFilterTest.toTestArgs(it) }
                .stream()

        @JvmStatic
        fun neutralEndpoints(): Stream<Arguments> = NON_GRAPH_ENDPOINTS.sorted().map { ReadOnlyRequestFilterTest.toTestArgs(it) }.stream()
    }
}
