package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeAggCountSentinelE2ETest : E2ETestBase() {
    private val db = "test-agg-count-db"
    private val table = "follows"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test"}""")
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
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Insert 3 outgoing edges from node 1 (1→2, 1→3, 1→4)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 2}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 3}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 4}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Insert 2 incoming edges to node 1 (5→1, 6→1)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 5, "target": 1}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 6, "target": 1}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `agg with __count__ sentinel returns OUT count`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=OUT")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.groups.length()").isEqualTo(1)
            .jsonPath("$.groups[0].value").isEqualTo(3)
            .jsonPath("$.groups[0].direction").isEqualTo("OUT")
    }

    @Test
    fun `agg with __count__ sentinel returns IN count`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=IN")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count").isEqualTo(1)
            .jsonPath("$.groups.length()").isEqualTo(1)
            .jsonPath("$.groups[0].value").isEqualTo(2)
            .jsonPath("$.groups[0].direction").isEqualTo("IN")
    }

    @Test
    fun `agg with __count__ and ranges returns 400`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=OUT&ranges=foo%3Dbar")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `agg with __count__ and multiple starts returns count per node`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&start=5&direction=OUT")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count").isEqualTo(2)
            .jsonPath("$.groups.length()").isEqualTo(2)
    }

    @Test
    fun `agg with regular group requires ranges`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/no_such_group?start=1&direction=OUT")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
