package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * E2E test for multi-hop query using ActionbaseQuery.
 *
 * Multi-hop = ActionbaseQuery with chained SCAN + CACHE items.
 * - hop1 (SCAN): index scan on follows table
 * - hop2 (CACHE): EdgeCache multi-get on wishlist table using hop1 results
 *
 * Step 1: Metadata creation (databases + tables with indexes and caches)
 * Step 2: Data insertion (edges for both hops)
 * Step 3: Multi-hop query via /graph/v3/query using ActionbaseQuery format
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActionbaseQueryCacheE2ETest : E2ETestBase() {
    private val db1 = "multihop-db1"
    private val db2 = "multihop-db2"
    private val hop1Table = "follows"
    private val hop2Table = "wishlist"

    // ── Step 1: Metadata + Step 2: Data insertion ───────────────────────

    @BeforeAll
    fun setup() {
        // Create databases
        for (db in listOf(db1, db2)) {
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        // hop1 table: EDGE with index
        client
            .post()
            .uri("/graph/v3/databases/$db1/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$hop1Table",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "createdAt", "type": "long", "comment": "ts", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [
                      {"index": "created_at_desc", "fields": [{"field": "createdAt", "order": "DESC"}]}
                    ],
                    "groups": []
                  },
                  "storage": "datastore://multihop_ns/follows",
                  "mode": "SYNC",
                  "comment": "hop1"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // hop2 table: EDGE with index + cache
        client
            .post()
            .uri("/graph/v3/databases/$db2/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$hop2Table",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "createdAt", "type": "long", "comment": "ts", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [
                      {"index": "created_at_desc", "fields": [{"field": "createdAt", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "recent_wishlist",
                        "fields": [{"field": "createdAt", "order": "DESC"}],
                        "limit": 100
                      }
                    ]
                  },
                  "storage": "datastore://multihop_ns/wishlist",
                  "mode": "SYNC",
                  "comment": "hop2 with cache"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // ── Step 2: Data insertion ────────────────────────────────────

        // hop1 edges: source=1000 follows targets=[2000, 2001]
        client
            .post()
            .uri("/graph/v3/databases/$db1/tables/$hop1Table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2000, "properties": {"createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2001, "properties": {"createdAt": 101}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // hop2 edges: 2000 wishlists [3000, 3001], 2001 wishlists [3002]
        client
            .post()
            .uri("/graph/v3/databases/$db2/tables/$hop2Table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 2000, "target": 3000, "properties": {"createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 2000, "target": 3001, "properties": {"createdAt": 201}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 2001, "target": 3002, "properties": {"createdAt": 202}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    // ── Step 3: Multi-hop query via ActionbaseQuery ─────────────────────

    @Nested
    inner class MultihopQuery {
        @Test
        fun `multihop scan+cache query via ActionbaseQuery`() {
            // hop1: SCAN follows index → hop2: CACHE wishlist using hop1 targets
            // TODO Phase 3: processCache returns real EdgeCache data instead of empty
            client
                .post()
                .uri("/graph/v3/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "query": [
                        {
                          "type": "SCAN",
                          "name": "hop1",
                          "service": "$db1",
                          "label": "$hop1Table",
                          "src": {"type": "VALUE", "value": [1000]},
                          "dir": "OUT",
                          "index": "created_at_desc",
                          "limit": 100,
                          "include": true
                        },
                        {
                          "type": "CACHE",
                          "name": "hop2",
                          "service": "$db2",
                          "label": "$hop2Table",
                          "src": {"type": "REF", "ref": "hop1", "field": "tgt"},
                          "dir": "OUT",
                          "cacheName": "recent_wishlist",
                          "limit": 10,
                          "include": true
                        }
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(2)
                .jsonPath("$.items[0].name")
                .isEqualTo("hop1")
                .jsonPath("$.items[0].rows")
                .isEqualTo(2)
                .jsonPath("$.items[1].name")
                .isEqualTo("hop2")
                .jsonPath("$.items[1].rows")
                .isEqualTo(0) // cache stub returns empty
        }

        @Test
        fun `scan-only query returns real data`() {
            client
                .post()
                .uri("/graph/v3/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "query": [
                        {
                          "type": "SCAN",
                          "name": "follows_scan",
                          "service": "$db1",
                          "label": "$hop1Table",
                          "src": {"type": "VALUE", "value": [1000]},
                          "dir": "OUT",
                          "index": "created_at_desc",
                          "limit": 100,
                          "include": true
                        }
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].name")
                .isEqualTo("follows_scan")
                .jsonPath("$.items[0].data.length()")
                .isEqualTo(2)
        }
    }
}
