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

// Adding a new endpoint without classifying it below breaks the exhaustiveness check.
// ServerRoleRequestFilterTest decides by the same four sets.
class ReadOnlyRequestFilterTest {
    private lateinit var filter: ReadOnlyRequestFilter

    @BeforeEach
    fun setup() {
        filter = ReadOnlyRequestFilter()
    }

    @Test
    fun `declared endpoints must match scanned controller annotations`() {
        val scanned = EndpointScanner.scan("com.kakao.actionbase.server.api").map { (m, p) -> "$m $p" }.toSet()
        val declared = READ_ENDPOINTS + WRITE_ENDPOINTS + CONTROL_ENDPOINTS + CONTROL_WRITE_ENDPOINTS + NON_GRAPH_ENDPOINTS

        val missing = scanned - declared
        val stale = declared - scanned

        assertTrue(missing.isEmpty(), "Not declared:\n${missing.joinToString("\n") { "  + $it" }}")
        assertTrue(stale.isEmpty(), "Stale:\n${stale.joinToString("\n") { "  - $it" }}")
    }

    @ParameterizedTest
    @MethodSource("readEndpoints")
    fun `should allow read requests on filtered path prefixes`(
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

    // No /control write exists yet, so nothing else would notice the prefix leaving the filter.
    @Test
    fun `should block writes on the control prefix`() {
        assertBlocked("POST", "/control/topology")
        assertBlocked("DELETE", "/control/anything")
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
                "GET /graph/v2/admin/metadata/storage",
                "GET /graph/v2/admin//migration/{name}",
                "GET /graph/v2/metastore/global",
                "GET /graph/v2/service",
                "GET /graph/v2/service/{service}",
                "GET /graph/v2/service/{service}/alias",
                "GET /graph/v2/service/{service}/alias/{alias}",
                "GET /graph/v2/service/{service}/label",
                "GET /graph/v2/service/{service}/label/{label}",
                "GET /graph/v2/service/{service}/label/{label}/edge",
                "GET /graph/v2/service/{service}/label/{label}/status",
                "GET /graph/v2/storage",
                "GET /graph/v2/storage/{storage}",
                // v3 GET
                "GET /graph/v3",
                "GET /aggregations/v1/metadata",
                "GET /graph/v3/databases",
                "GET /graph/v3/databases/{database}",
                "GET /graph/v3/databases/{database}/aliases",
                "GET /graph/v3/databases/{database}/aliases/{alias}",
                "GET /graph/v3/databases/{database}/tables",
                "GET /graph/v3/databases/{database}/tables/{table}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/agg/{group}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/seek/{cache}",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/count",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/counts",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/get",
                "GET /graph/v3/databases/{database}/tables/{table}/edges/scan/{index}",
                "GET /graph/v3/databases/{database}/tables/{table}/multi-edges/ids",
                "GET /graph/v3/databases/{database}/tables/{table}/vertices/get",
                "GET /graph/v3/datastore",
                "GET /graph/v3/datastore/hbase/namespaces",
                "GET /graph/v3/datastore/hbase/references",
                "GET /graph/v3/datastore/hbase/tables",
                "GET /graph/v3/datastore/hbase/tables/{tableName}",
                "GET /graph/v3/datastore/hbase/tables/{tableName}/metric",
                "GET /graph/v3/datastore/hbase/tables/{tableName}/references",
                // read-only POST
                "POST /graph/v3/query",
                "POST /graph/v3/databases/{database}/tables/{table}/edges/get",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges/ids",
                "POST /aggregations/v1/databases/{database}/tables/{table}/topks/{topk}",
                // queue/v1 GET
                "GET /queue/v1/namespaces/{namespace}/queues/{queue}",
                "GET /queue/v1/namespaces/{namespace}/queues/{queue}/partitions",
                "GET /queue/v1/namespaces/{namespace}/queues/{queue}/partitions/{partition}/poll",
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
                "DELETE /graph/v2/service/{service}/alias/{alias}",
                "DELETE /graph/v2/service/{service}/label/{label}/edge",
                "DELETE /graph/v2/service/{service}/label/{label}/edge/purge",
                "DELETE /graph/v2/service/{service}/label/{label}/edge/sync",
                "POST /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "POST /graph/v2/edge",
                "POST /graph/v2/service/{service}",
                "POST /graph/v2/service/{service}/alias/{alias}",
                "POST /graph/v2/service/{service}/label/{label}",
                "POST /graph/v2/service/{service}/label/{label}/edge",
                "POST /graph/v2/service/{service}/label/{label}/edge/delete",
                "POST /graph/v2/service/{service}/label/{label}/edge/purge",
                "POST /graph/v2/service/{service}/label/{label}/edge/sync",
                "PUT /graph/v2/admin/hbase/cluster/{cluster}/table/{tableFullName}",
                "PUT /graph/v2/edge",
                "PUT /graph/v2/service/{service}",
                "PUT /graph/v2/service/{service}/alias/{alias}",
                "PUT /graph/v2/service/{service}/label/{label}",
                "PUT /graph/v2/service/{service}/label/{label}/edge",
                "PUT /graph/v2/service/{service}/label/{label}/edge/sync",
                "PUT /graph/v2/storage/{storage}",
                // v3 mutation
                "DELETE /graph/v3/databases/{database}",
                "DELETE /graph/v3/databases/{database}/aliases/{alias}",
                "DELETE /graph/v3/databases/{database}/tables/{table}",
                "DELETE /graph/v3/databases/{database}/tables/{table}/edges/scan/{index}",
                "POST /aggregations/v1/aggregate",
                "POST /aggregations/v1/sweep",
                "POST /graph/v3/databases",
                "POST /graph/v3/databases/{database}/aliases",
                "POST /graph/v3/databases/{database}/tables",
                "POST /graph/v3/databases/{database}/tables/{table}/edges",
                "POST /graph/v3/databases/{database}/tables/{table}/edges/sync",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges",
                "POST /graph/v3/databases/{database}/tables/{table}/multi-edges/sync",
                "POST /graph/v3/databases/{database}/tables/{table}/vertices",
                "POST /graph/v3/databases/{database}/tables/{table}/vertices/sync",
                "PUT /graph/v3/databases/{database}",
                "PUT /graph/v3/databases/{database}/aliases/{alias}",
                "PUT /graph/v3/databases/{database}/tables/{table}",
                // v3 hbase datastore mutation
                "DELETE /graph/v3/datastore/hbase/tables/{tableName}",
                "POST /graph/v3/datastore/hbase/tables/{tableName}",
                "POST /graph/v3/datastore/hbase/tables/{tableName}/disable",
                "POST /graph/v3/datastore/hbase/tables/{tableName}/enable",
                "POST /graph/v3/datastore/hbase/tables/{tableName}/replication/disable",
                "POST /graph/v3/datastore/hbase/tables/{tableName}/replication/enable",
                "PUT /graph/v3/datastore/hbase/tables/{tableName}",
                // queue/v1 mutation
                "POST /queue/v1/namespaces/{namespace}/queues",
                "POST /queue/v1/namespaces/{namespace}/queues/{queue}/messages",
                "PUT /queue/v1/namespaces/{namespace}/queues/{queue}/enable",
                "PUT /queue/v1/namespaces/{namespace}/queues/{queue}/disable",
                "DELETE /queue/v1/namespaces/{namespace}/queues/{queue}",
                "DELETE /queue/v1/namespaces/{namespace}/queues/{queue}/partitions/{partition}/messages",
            )

        val CONTROL_ENDPOINTS =
            setOf(
                "GET /control/topology",
                "GET /control/topology/{tenant}",
                "GET /control/tenants/{tenant}/datastore/tables",
                "GET /control/htables",
            )

        // Operational writes: refused by a read-only instance like any other write, but served by a
        // control instance - which is why they cannot simply join WRITE_ENDPOINTS.
        val CONTROL_WRITE_ENDPOINTS =
            setOf(
                "POST /control/jobs",
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
        fun readEndpoints(): Stream<Arguments> = (READ_ENDPOINTS + CONTROL_ENDPOINTS).sorted().map { toTestArgs(it) }.stream()

        @JvmStatic
        fun writeEndpoints(): Stream<Arguments> = (WRITE_ENDPOINTS + CONTROL_WRITE_ENDPOINTS).sorted().map { toTestArgs(it) }.stream()

        @JvmStatic
        fun nonGraphEndpoints(): Stream<Arguments> =
            NON_GRAPH_ENDPOINTS
                .filter { !it.startsWith("GET ") }
                .sorted()
                .map { toTestArgs(it) }
                .stream()

        fun toTestArgs(endpoint: String): Arguments {
            val (method, path) = endpoint.split(" ", limit = 2)
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
                "namespace" to "n",
                "partition" to "0",
                "queue" to "q",
                "service" to "s",
                "storage" to "st",
                "table" to "t",
                "tableFullName" to "t",
                "tableName" to "t",
                "tenant" to "alpha",
                "topk" to "tk",
            )

        private fun resolvePath(template: String): String =
            template.replace(Regex("\\{([^}]+)\\}")) {
                PATH_VARS[it.groupValues[1]]
                    ?: error("Unknown path variable '${it.groupValues[1]}' in $template. Add it to PATH_VARS.")
            }
    }
}
