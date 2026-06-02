package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * Verifies that payload-deterministic validation failures return HTTP 400
 * and that valid mutations are not affected (regression guard).
 *
 * Part 1: invalid payload is rejected before WAL publication on both
 * the async `/edges` endpoint and the sync `/edges/sync` endpoint.
 *
 * Part 2A (discriminating onErrorResume): IllegalArgumentException from
 * the RMW branch propagates as 400 rather than being swallowed as 200+ERROR.
 * This is structural — the primary cases (INSERT/UPDATE payload violations)
 * are already caught by Part 1 before any WAL write.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeMutationValidationTest : E2ETestBase() {
    private val db = "validation-test-db"
    private val syncTable = "validation-sync-edge"
    private val asyncTable = "validation-async-edge"

    // Schema has "score" (long, non-nullable) and "tag" (string, nullable).
    private val syncTableDdl =
        """
        {
          "table": "$syncTable",
          "schema": {
            "type": "EDGE",
            "source": {"type": "string", "comment": "src"},
            "target": {"type": "string", "comment": "tgt"},
            "properties": [
              {"name": "score", "type": "long", "comment": "score", "nullable": false},
              {"name": "tag",   "type": "string", "comment": "tag",   "nullable": true}
            ],
            "direction": "OUT",
            "indexes": [],
            "groups": []
          },
          "storage": "datastore://test_namespace/validation_sync_edge",
          "mode": "SYNC",
          "comment": "sync edge for validation test"
        }
        """.trimIndent()

    private val asyncTableDdl =
        """
        {
          "table": "$asyncTable",
          "schema": {
            "type": "EDGE",
            "source": {"type": "string", "comment": "src"},
            "target": {"type": "string", "comment": "tgt"},
            "properties": [
              {"name": "score", "type": "long", "comment": "score", "nullable": false},
              {"name": "tag",   "type": "string", "comment": "tag",   "nullable": true}
            ],
            "direction": "OUT",
            "indexes": [],
            "groups": []
          },
          "storage": "datastore://test_namespace/validation_async_edge",
          "mode": "ASYNC",
          "comment": "async edge for validation test"
        }
        """.trimIndent()

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "validation test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(syncTableDdl)
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(asyncTableDdl)
            .exchange()
            .expectStatus()
            .isOk
    }

    // ---- Part 1: pre-WAL validation on /edges/sync (sync path) ----

    @Test
    fun `sync INSERT missing non-nullable field returns 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$syncTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"tag":"hello"}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `sync INSERT with explicit null for non-nullable field returns 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$syncTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"INSERT","edge":{"version":2,"source":"A","target":"B","properties":{"score":null,"tag":"hello"}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `sync UPDATE with explicit null for non-nullable field returns 400`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$syncTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"UPDATE","edge":{"version":3,"source":"A","target":"B","properties":{"score":null}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `sync INSERT with all required fields returns 200`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$syncTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"INSERT","edge":{"version":10,"source":"X","target":"Y","properties":{"score":42}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
    }

    // ---- Part 1: pre-WAL validation on /edges (async path) ----

    @Test
    fun `async INSERT missing non-nullable field returns 400 (not QUEUED)`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$asyncTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"tag":"hello"}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `async INSERT with all required fields returns 200 with QUEUED status`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$asyncTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"mutations":[
                  {"type":"INSERT","edge":{"version":10,"source":"X","target":"Y","properties":{"score":99}}}
                ]}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("QUEUED")
    }
}
