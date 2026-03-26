package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeCacheQueryE2ETest : E2ETestBase() {
    private val db = "cache-query-db"
    private val table = "wishlist"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "cache query test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$table",
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
                        "limit": 100
                      }
                    ]
                  },
                  "storage": "datastore://cache_query_ns/wishlist",
                  "mode": "SYNC",
                  "comment": "edge with cache"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
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
    }

    @Nested
    inner class CacheEndpoint {
        @Test
        fun `cache query returns empty result as stub`() {
            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$table/edges/cache/recent_wishlist?start=1000&direction=OUT")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.edges")
                .isArray
                .jsonPath("$.count")
                .isEqualTo(0)
                .jsonPath("$.hasNext")
                .isEqualTo(false)
        }

        @Test
        fun `cache query with unknown table returns empty stub`() {
            client
                .get()
                .uri("/graph/v3/databases/$db/tables/nonexistent/edges/cache/recent_wishlist?start=1000&direction=OUT")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.count")
                .isEqualTo(0)
        }
    }
}
