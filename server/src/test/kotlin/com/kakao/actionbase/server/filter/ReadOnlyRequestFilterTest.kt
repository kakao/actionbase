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

    // -- GET requests: always allowed --

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - path: /graph/v2
        - path: /graph/v3
        - path: /graph/v2/service/s
        - path: /graph/v2/service
        - path: /graph/v2/service/s/label/l
        - path: /graph/v2/service/s/label
        - path: /graph/v2/service/s/label/l/status
        - path: /graph/v2/service/s/label/l/edge
        - path: /graph/v2/service/s/label/l/edge/id/e1
        - path: /graph/v2/service/s/alias/a
        - path: /graph/v2/service/s/alias
        - path: /graph/v2/service/s/query/q
        - path: /graph/v2/service/s/query
        - path: /graph/v2/storage/st
        - path: /graph/v2/storage
        - path: /graph/v2/metastore/global
        - path: /graph/v2/metastore/local
        - path: /graph/v2/admin/labels
        - path: /graph/v2/admin/dump
        - path: /graph/v2/admin/metadata/service
        - path: /graph/v2/admin/metadata/storage
        - path: /graph/v2/admin/metadata/service/s/label
        - path: /graph/v2/admin/metadata/service/s/alias
        - path: /graph/v2/admin/metadata/service/s/query
        - path: /graph/v2/admin/hbase/cluster
        - path: /graph/v2/admin/hbase/cluster/c1
        - path: /graph/v2/admin/hbase/cluster/c1/table
        - path: /graph/v2/admin/hbase/cluster/c1/table/t1
        - path: /graph/v2/admin/hbase/cluster/c1/table/t1/metric
        - path: /graph/v2/admin/hbase/cluster/c1/replication
        - path: /graph/v3/databases
        - path: /graph/v3/databases/db
        - path: /graph/v3/databases/db/tables
        - path: /graph/v3/databases/db/tables/t
        - path: /graph/v3/databases/db/aliases
        - path: /graph/v3/databases/db/aliases/a
        - path: /graph/v3/databases/db/tables/t/edges/get
        - path: /graph/v3/databases/db/tables/t/edges/count
        - path: /graph/v3/databases/db/tables/t/edges/counts
        - path: /graph/v3/databases/db/tables/t/edges/scan/ts_desc
        - path: /graph/v3/databases/db/tables/t/edges/agg/group
        - path: /graph/v3/databases/db/tables/t/multi-edges/ids
        - path: /graph/v3/datastore
        - path: /graph/v3/datastore/hbase/namespaces
        - path: /graph/v3/datastore/hbase/tables
        - path: /graph/v3/datastore/hbase/tables/t1
        - path: /graph/v3/datastore/hbase/tables/t1/metric
        - path: /graph/health
        - path: /graph/health/readiness
        - path: /graph/health/liveness
        """,
    )
    fun `should allow GET requests`(path: String) {
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

    // -- Read-only POST requests: allowed (query endpoints that use POST for complex request bodies) --

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - path: /graph/v2/query
        - path: /graph/v3/query
        - path: /graph/v3/databases/db/tables/t/edges/get
        - path: /graph/v3/databases/db/tables/t/multi-edges/ids
        """,
    )
    fun `should allow read-only POST requests on graph paths`(path: String) {
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

    // -- Write requests outside protected path prefixes (/graph/v2, /graph/v3): always allowed --

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: PUT
          path: /graph/health/readiness
        """,
    )
    fun `should allow write requests outside protected path prefixes`(
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

        assertTrue(chainCalled.get(), "Expected $method $path to be allowed (non-graph)")
    }

    // -- Write requests on graph paths: blocked --

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: POST
          path: /graph/v2/service/s
        - method: PUT
          path: /graph/v2/service/s
        - method: POST
          path: /graph/v2/service/s/label/l
        - method: PUT
          path: /graph/v2/service/s/label/l
        - method: POST
          path: /graph/v2/service/s/label/l/copy
        - method: POST
          path: /graph/v2/service/s/label/l/edge
        - method: PUT
          path: /graph/v2/service/s/label/l/edge
        - method: DELETE
          path: /graph/v2/service/s/label/l/edge
        - method: POST
          path: /graph/v2/service/s/label/l/edge/id/e1
        - method: PUT
          path: /graph/v2/service/s/label/l/edge/id/e1
        - method: DELETE
          path: /graph/v2/service/s/label/l/edge/id/e1
        - method: POST
          path: /graph/v2/service/s/label/l/edge/delete
        - method: POST
          path: /graph/v2/service/s/label/l/edge/delete/id/e1
        - method: POST
          path: /graph/v2/service/s/label/l/edge/sync
        - method: PUT
          path: /graph/v2/service/s/label/l/edge/sync
        - method: DELETE
          path: /graph/v2/service/s/label/l/edge/sync
        - method: DELETE
          path: /graph/v2/service/s/label/l/edge/purge
        - method: POST
          path: /graph/v2/service/s/label/l/edge/purge
        - method: POST
          path: /graph/v2/service/s/alias/a
        - method: PUT
          path: /graph/v2/service/s/alias/a
        - method: DELETE
          path: /graph/v2/service/s/alias/a
        - method: POST
          path: /graph/v2/service/s/alias/a/new-label
        - method: POST
          path: /graph/v2/service/s/query/q
        - method: PUT
          path: /graph/v2/service/s/query/q
        - method: POST
          path: /graph/v2/storage/st
        - method: PUT
          path: /graph/v2/storage/st
        - method: POST
          path: /graph/v2/edge
        - method: PUT
          path: /graph/v2/edge
        - method: DELETE
          path: /graph/v2/edge
        - method: POST
          path: /graph/v2/edge/id
        - method: PUT
          path: /graph/v2/edge/id
        - method: DELETE
          path: /graph/v2/edge/id
        - method: DELETE
          path: /graph/v2/admin/service/s/label/l
        - method: DELETE
          path: /graph/v2/admin/service/s/alias/a
        - method: DELETE
          path: /graph/v2/admin/storage/st
        - method: DELETE
          path: /graph/v2/admin/service/s
        - method: DELETE
          path: /graph/v2/admin/hbase/cluster/c1/table/t1
        - method: PUT
          path: /graph/v2/admin/hbase/cluster/c1/table/t1
        - method: POST
          path: /graph/v2/admin/hbase/cluster/c1/table/t1
        - method: POST
          path: /graph/v3/databases
        - method: PUT
          path: /graph/v3/databases/db
        - method: DELETE
          path: /graph/v3/databases/db
        - method: POST
          path: /graph/v3/databases/db/tables
        - method: PUT
          path: /graph/v3/databases/db/tables/t
        - method: DELETE
          path: /graph/v3/databases/db/tables/t
        - method: POST
          path: /graph/v3/databases/db/aliases
        - method: PUT
          path: /graph/v3/databases/db/aliases/a
        - method: DELETE
          path: /graph/v3/databases/db/aliases/a
        - method: POST
          path: /graph/v3/databases/db/tables/t/edges
        - method: POST
          path: /graph/v3/databases/db/tables/t/edges/sync
        - method: POST
          path: /graph/v3/databases/db/tables/t/multi-edges
        - method: POST
          path: /graph/v3/databases/db/tables/t/multi-edges/sync
        - method: POST
          path: /graph/v3/datastore/hbase/tables/t1
        - method: PUT
          path: /graph/v3/datastore/hbase/tables/t1
        - method: DELETE
          path: /graph/v3/datastore/hbase/tables/t1
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
        val path = "/graph/v3/databases"
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
}
