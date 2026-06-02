package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.StatusAssertions

/**
 * Verifies that schema-invalid payloads return HTTP 400 on both the async `/edges`
 * and sync `/edges/sync` endpoints, and that valid payloads are unaffected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeMutationValidationTest : E2ETestBase() {
    private val db = "validation-test-db"
    private val syncTable = "validation-sync-edge"
    private val asyncTable = "validation-async-edge"

    // Schema: "score" (long, non-nullable), "tag" (string, nullable)
    private fun tableDdl(
        table: String,
        storage: String,
        mode: String,
    ) = """
        {
          "table": "$table",
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
          "storage": "$storage",
          "mode": "$mode",
          "comment": "$mode edge for validation test"
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
            .bodyValue(tableDdl(syncTable, "datastore://test_namespace/validation_sync_edge", "SYNC"))
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(tableDdl(asyncTable, "datastore://test_namespace/validation_async_edge", "ASYNC"))
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun mutateSync(body: String): StatusAssertions =
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$syncTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()

    private fun mutateAsync(body: String): StatusAssertions =
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$asyncTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()

    @Test
    fun `sync INSERT missing non-nullable field returns 400`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"tag":"hello"}}}]}""")
            .isBadRequest
    }

    @Test
    fun `sync INSERT with explicit null for non-nullable field returns 400`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":2,"source":"A","target":"B","properties":{"score":null,"tag":"hello"}}}]}""")
            .isBadRequest
    }

    @Test
    fun `sync UPDATE with explicit null for non-nullable field returns 400`() {
        mutateSync("""{"mutations":[{"type":"UPDATE","edge":{"version":3,"source":"A","target":"B","properties":{"score":null}}}]}""")
            .isBadRequest
    }

    @Test
    fun `sync INSERT with all required fields returns 200`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":10,"source":"X","target":"Y","properties":{"score":42}}}]}""")
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
    }

    @Test
    fun `async INSERT missing non-nullable field returns 400`() {
        mutateAsync("""{"mutations":[{"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"tag":"hello"}}}]}""")
            .isBadRequest
    }
}
