package com.kakao.actionbase.server.filter

import com.kakao.actionbase.server.test.EndpointScanner

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

// Test strategy:
// 1. Scan all @RestController endpoints via reflection at test time.
// 2. Compare scanned set against READ/WRITE/NON_GRAPH constants — any mismatch fails the build.
// 3. Run each endpoint through the filter: READ → allowed, WRITE → 403, NON_GRAPH → allowed.
// Adding a new endpoint without classifying it here will break the exhaustiveness check.
class ReadOnlyRequestFilterTest {
    private lateinit var filter: ReadOnlyRequestFilter

    @BeforeEach
    fun setup() {
        filter = ReadOnlyRequestFilter()
    }

    @Test
    fun `declared endpoints must match scanned controller annotations`() {
        val scanned = EndpointScanner.scan("com.kakao.actionbase.server.api").map { (m, p) -> "$m $p" }.toSet()
        val declared = READ_ENDPOINTS + WRITE_ENDPOINTS + NON_GRAPH_ENDPOINTS

        val missing = scanned - declared
        val stale = declared - scanned

        assertTrue(missing.isEmpty(), "Not declared:\n${missing.joinToString("\n") { "  + $it" }}")
        assertTrue(stale.isEmpty(), "Stale:\n${stale.joinToString("\n") { "  - $it" }}")
    }

    @ParameterizedTest
    @MethodSource("readEndpoints")
    fun `should allow read requests on graph paths`(
        method: String,
        path: String,
    ) {
        assertAllowed(method, path)
    }

    @ParameterizedTest
    @MethodSource("writeEndpoints")
    fun `should block write requests on graph paths`(
        method: String,
        path: String,
    ) {
        assertBlocked(method, path)
    }

    @ParameterizedTest
    @MethodSource("nonGraphEndpoints")
    fun `should allow requests outside graph path prefixes`(
        method: String,
        path: String,
    ) {
        assertAllowed(method, path)
    }

    @Test
    fun `should include method and path in error response body`() {
        val path = "/graph/v3/databases"
        val exchange = buildExchange("POST", path)
        filter.filter(exchange, WebFilterChain { Mono.empty() }).block()

        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("POST"))
        assertTrue(body.contains(path))
        assertTrue(body.contains("read-only"))
    }

    private fun assertAllowed(
        method: String,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        var passed = false
        filter
            .filter(
                exchange,
                WebFilterChain {
                    passed = true
                    Mono.empty()
                },
            ).block()
        assertTrue(passed, "Expected $method $path to be allowed")
    }

    private fun assertBlocked(
        method: String,
        path: String,
    ) {
        val exchange = buildExchange(method, path)
        var passed = false
        filter
            .filter(
                exchange,
                WebFilterChain {
                    passed = true
                    Mono.empty()
                },
            ).block()
        assertFalse(passed, "Expected $method $path to be blocked")
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
        assertEquals(org.springframework.http.MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
    }

    private fun buildExchange(
        method: String,
        path: String,
    ): MockServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path))

    companion object {
        val READ_ENDPOINTS =
            setOf(
                // v2 GET
                "GET /graph/v2",
                "GET /graph/v2/admin/dump",
                "GET /graph/v2/admin/hbase/cluster",
                "GET /graph/v2/admin/hbase/cluster/{cluster}",
                "GET /graph/v2/admin/hbase/cluster/{cluster}/replication",
                "GET /graph/v2/admin/hbase/cluster/{cluster}/table",
                "GET /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "GET /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}/metric",
                "GET /graph/v2/admin/labels",
                "GET /graph/v2/admin/metadata/service",
                "GET /graph/v2/admin/metadata/service/{service}/alias",
                "GET /graph/v2/admin/metadata/service/{service}/label",
                "GET /graph/v2/admin/metadata/service/{service}/query",
                "GET /graph/v2/admin/metadata/storage",
                "GET /graph/v2/admin//migration/{name}",
                "GET /graph/v2/metastore/global",
                "GET /graph/v2/metastore/local",
                "GET /graph/v2/service",
                "GET /graph/v2/service/{service}",
                "GET /graph/v2/service/{service}/alias",
                "GET /graph/v2/service/{service}/alias/{alias}",
                "GET /graph/v2/service/{service}/label",
                "GET /graph/v2/service/{service}/label/{label}",
                "GET /graph/v2/service/{service}/label/{label}/edge",
                "GET /graph/v2/service/{service}/label/{label}/edge/id/{edgeId}",
                "GET /graph/v2/service/{service}/label/{label}/status",
                "GET /graph/v2/service/{service}/query",
                "GET /graph/v2/service/{service}/query/{query}",
                "GET /graph/v2/storage",
                "GET /graph/v2/storage/{storage}",
                // v3 GET
                "GET /graph/v3",
                "GET /graph/v3/databases",
                "GET /graph/v3/databases/{database}",
                "GET /graph/v3/databases/{database}/aliases",
                "GET /graph/v3/databases/{database}/aliases/{alias}",
                "GET /graph/v3/databases/{database}/tables",
                "GET /graph/v3/databases/{database}/tables/{table}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/agg/{group}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/cache/{cache}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/count",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/counts",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/get",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/scan/{index}",
                "GET /graph/v3/databases/{database}/tables/{table}/multi-edges/ids",
                "GET /graph/v3/datastore",
                // read-only POST
                "POST /graph/v3/query",
                "POST /graph/v3/databases/{database}/tables/{table}/edges/get",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges/ids",
            )

        val WRITE_ENDPOINTS =
            setOf(
                // v2 mutation
                "DELETE /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "DELETE /graph/v2/admin/service/{service}",
                "DELETE /graph/v2/admin/service/{service}/alias/{alias}",
                "DELETE /graph/v2/admin/service/{service}/label/{label}",
                "DELETE /graph/v2/admin/storage/{storage}",
                "DELETE /graph/v2/edge",
                "DELETE /graph/v2/edge/id",
                "DELETE /graph/v2/service/{service}/alias/{alias}",
                "DELETE /graph/v2/service/{service}/label/{label}/edge",
                "DELETE /graph/v2/service/{service}/label/{label}/edge/id/{edgeId}",
                "DELETE /graph/v2/service/{service}/label/{label}/edge/purge",
                "DELETE /graph/v2/service/{service}/label/{label}/edge/sync",
                "POST /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "POST /graph/v2/edge",
                "POST /graph/v2/edge/id",
                "POST /graph/v2/service/{service}",
                "POST /graph/v2/service/{service}/alias/{alias}",
                "POST /graph/v2/service/{service}/alias/{alias}/new-label",
                "POST /graph/v2/service/{service}/label/{label}",
                "POST /graph/v2/service/{service}/label/{label}/copy",
                "POST /graph/v2/service/{service}/label/{label}/edge",
                "POST /graph/v2/service/{service}/label/{label}/edge/delete",
                "POST /graph/v2/service/{service}/label/{label}/edge/delete/id/{edgeId}",
                "POST /graph/v2/service/{service}/label/{label}/edge/id/{edgeId}",
                "POST /graph/v2/service/{service}/label/{label}/edge/purge",
                "POST /graph/v2/service/{service}/label/{label}/edge/sync",
                "POST /graph/v2/service/{service}/query/{query}",
                "POST /graph/v2/storage/{storage}",
                "PUT /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "PUT /graph/v2/edge",
                "PUT /graph/v2/edge/id",
                "PUT /graph/v2/service/{service}",
                "PUT /graph/v2/service/{service}/alias/{alias}",
                "PUT /graph/v2/service/{service}/label/{label}",
                "PUT /graph/v2/service/{service}/label/{label}/edge",
                "PUT /graph/v2/service/{service}/label/{label}/edge/id/{edgeId}",
                "PUT /graph/v2/service/{service}/label/{label}/edge/sync",
                "PUT /graph/v2/service/{service}/query/{query}",
                "PUT /graph/v2/storage/{storage}",
                // v3 mutation
                "DELETE /graph/v3/databases/{database}",
                "DELETE /graph/v3/databases/{database}/aliases/{alias}",
                "DELETE /graph/v3/databases/{database}/tables/{table}",
                "POST /graph/v3/databases",
                "POST /graph/v3/databases/{database}/aliases",
                "POST /graph/v3/databases/{database}/tables",
                "POST /graph/v3/databases/{database}/tables/{table}/edges",
                "POST /graph/v3/databases/{database}/tables/{table}/edges/sync",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges/sync",
                "PUT /graph/v3/databases/{database}",
                "PUT /graph/v3/databases/{database}/aliases/{alias}",
                "PUT /graph/v3/databases/{database}/tables/{table}",
            )

        val NON_GRAPH_ENDPOINTS =
            setOf(
                "GET /",
                "GET /graph",
                "GET /graph/check/delay_with_cache",
                "GET /graph/check/delay_without_cache",
                "GET /graph/check/emoji",
                "GET /graph/check/error",
                "GET /graph/check/mono",
                "GET /graph/check/response-meta",
                "GET /graph/check/sentry",
                "GET /graph/health",
                "GET /graph/health/liveness",
                "GET /graph/health/readiness",
                "PUT /graph/health/readiness",
            )

        @JvmStatic
        fun readEndpoints(): Stream<Arguments> = READ_ENDPOINTS.sorted().map { it.toTestArgs() }.stream()

        @JvmStatic
        fun writeEndpoints(): Stream<Arguments> = WRITE_ENDPOINTS.sorted().map { it.toTestArgs() }.stream()

        @JvmStatic
        fun nonGraphEndpoints(): Stream<Arguments> =
            NON_GRAPH_ENDPOINTS
                .filter { !it.startsWith("GET ") }
                .sorted()
                .map { it.toTestArgs() }
                .stream()

        private fun String.toTestArgs(): Arguments {
            val (method, path) = split(" ", limit = 2)
            return Arguments.of(method, resolvePath(path))
        }

        private val PATH_VARS =
            mapOf(
                "alias" to "a",
                "cache" to "c",
                "cluster" to "c",
                "database" to "db",
                "edgeId" to "e",
                "group" to "g",
                "index" to "idx",
                "label" to "l",
                "name" to "n",
                "query" to "q",
                "service" to "s",
                "storage" to "st",
                "table" to "t",
                "tableFullName" to "t",
                "tableName" to "t",
            )

        private fun resolvePath(template: String): String =
            template.replace(Regex("\\{([^}]+)\\}")) {
                PATH_VARS[it.groupValues[1]]
                    ?: error("Unknown path variable '${it.groupValues[1]}' in $template. Add it to PATH_VARS.")
            }
    }
}
