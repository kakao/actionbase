package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * E2E coverage for the prune endpoint `POST .../edges/prune`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeCachePruneE2ETest : E2ETestBase() {
    private val db = "prune-test-db"
    private val edgeTable = "wishlist"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "prune test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        // Small limit/tolerance so the disabled round-trip can exercise eviction (retain 3).
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$edgeTable",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "createdAt", "type": "long", "comment": "ts", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "recent_wishlist",
                        "fields": [{"field": "createdAt", "order": "DESC"}],
                        "limit": 2,
                        "tolerance": 1
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/wishlist",
                  "mode": "SYNC",
                  "comment": "edge with cache"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2000, "properties": {"createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2001, "properties": {"createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2002, "properties": {"createdAt": 300}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2003, "properties": {"createdAt": 400}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2004, "properties": {"createdAt": 500}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    /** Valid request routes end-to-end and returns the contract-shaped body (mocked: empty). */
    @Test
    fun `prune routes and returns results envelope`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/prune")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "CACHE", "targets": [{"start": 1000, "direction": "OUT"}]}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results")
            .isArray
    }

    /**
     * Acceptance test for the prune data plane (enable with the implementation PR).
     *
     *
     * EdgeCache (source=1000, direction=OUT, cache=recent_wishlist)
     * recent_wishlist: limit=2, tolerance=1
     *
     * Before:
     * |       row key        | qualifier (DESC) |           value          |
     * |----------------------|------------------|--------------------------|
     * | hash|1000|T|-6|OUT|C | ~500 | 2004      | version=1, createdAt=500 |
     * |                      | ~400 | 2003      | version=1, createdAt=400 | ← limit
     * |                      | ~300 | 2002      | version=1, createdAt=300 | ← tolerance
     * |                      | ~200 | 2001      | version=1, createdAt=200 | ← over-limit
     * |                      | ~100 | 2000      | version=1, createdAt=100 | ← over-limit
     *
     * After:
     * |       row key        | qualifier (DESC) |           value          |
     * |----------------------|------------------|--------------------------|
     * | hash|1000|T|-6|OUT|C | ~500 | 2004      | version=1, createdAt=500 |
     * |                      | ~400 | 2003      | version=1, createdAt=400 |
     * |                      | ~300 | 2002      | version=1, createdAt=300 |
     *
     * Expected: results=[PRUNED], seek OUT → [2004, 2003, 2002]
     */
    @Test
    @Disabled("enable when EdgeCache prune data plane is implemented")
    fun `prune evicts entries beyond limit + tolerance`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/prune")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type": "CACHE", "targets": [{"start": 1000, "direction": "OUT"}]}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(1)
            .jsonPath("$.results[0].type")
            .isEqualTo("CACHE")
            .jsonPath("$.results[0].name")
            .isEqualTo("recent_wishlist")
            .jsonPath("$.results[0].status")
            .isEqualTo("PRUNED")

        // Retained top-3 by createdAt DESC: 2004(500), 2003(400), 2002(300).
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist?start=1000&direction=OUT&limit=10")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(3)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2004)
            .jsonPath("$.edges[2].target")
            .isEqualTo(2002)
    }
}
