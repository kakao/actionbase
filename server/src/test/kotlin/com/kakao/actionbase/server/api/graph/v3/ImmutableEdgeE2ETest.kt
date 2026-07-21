package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * E2E for the immutable edge table: append persists index rows only (no State), scan reads
 * them back in index order, point get is rejected (400), and non-INSERT mutations are rejected
 * (append-only). Runs against the in-memory datastore backend via `DatastoreIndexedLabel`,
 * which reuses the same `V2BackedTableBinding` as HBase.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImmutableEdgeE2ETest : E2ETestBase() {
    private val db = "immutable_db"
    private val table = "immutable_log"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "immutable edge e2e db"}""")
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
                    "type": "IMMUTABLE_EDGE",
                    "source": {"type": "long", "comment": "partition"},
                    "target": {"type": "string", "comment": "message id"},
                    "properties": [
                      {"name": "seq", "type": "long", "comment": "sequence", "nullable": false},
                      {"name": "payload", "type": "string", "comment": "payload", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [{"index": "seq_asc", "fields": [{"field": "seq", "order": "ASC"}]}],
                    "groups": []
                  },
                  "storage": "datastore://immutable_ns/immutable_log",
                  "mode": "SYNC",
                  "comment": "append-only log"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    private fun append() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1000, "source": 1, "target": "m1", "properties": {"seq": 1000, "payload": "a"}}},
                    {"type": "INSERT", "edge": {"version": 1001, "source": 1, "target": "m2", "properties": {"seq": 1001, "payload": "b"}}},
                    {"type": "INSERT", "edge": {"version": 1002, "source": 1, "target": "m3", "properties": {"seq": 1002, "payload": "c"}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results.length()")
            .isEqualTo(3)
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
    }

    @Test
    fun `append then scan returns edges in index order`() {
        append()

        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/scan/seq_asc?start=1&direction=OUT&limit=10")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.edges.length()")
            .isEqualTo(3)
            .jsonPath("$.edges[0].target")
            .isEqualTo("m1")
            .jsonPath("$.edges[0].properties.payload")
            .isEqualTo("a")
            .jsonPath("$.edges[2].target")
            .isEqualTo("m3")
    }

    @Test
    fun `creating an immutable table with two indexes is rejected with 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "two_index_log",
                  "schema": {
                    "type": "IMMUTABLE_EDGE",
                    "source": {"type": "long", "comment": "partition"},
                    "target": {"type": "string", "comment": "message id"},
                    "properties": [
                      {"name": "seq", "type": "long", "comment": "sequence", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "seq_asc", "fields": [{"field": "seq", "order": "ASC"}]},
                      {"index": "seq_desc", "fields": [{"field": "seq", "order": "DESC"}]}
                    ],
                    "groups": []
                  },
                  "storage": "datastore://immutable_ns/two_index_log",
                  "mode": "SYNC",
                  "comment": "two indexes should be rejected"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `point get is rejected with 400`() {
        append()

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges/get")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"source": [1], "target": ["m1"]}""")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `UPDATE mutation is rejected with 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "UPDATE", "edge": {"version": 2000, "source": 1, "target": "m1", "properties": {"seq": 2000}}}]}""",
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `DELETE mutation is rejected with 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "DELETE", "edge": {"version": 2000, "source": 1, "target": "m1", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isBadRequest
    }
}
