package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.StatusAssertions

/**
 * E2E for a database under `actionbase.feature-flags` with `INSERT_MERGE`: an omitted field is a
 * legal partial write (per-item INVALID if the row stays incomplete); an explicit null on a
 * non-nullable field is still 400. Snapshot behavior is in [EdgeMutationValidationTest].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = [
        "actionbase.feature-flags[0].feature=INSERT_MERGE",
        "actionbase.feature-flags[0].scope.databases[0]=merge_test_db",
    ],
)
class EdgeMutationInsertMergeTest : E2ETestBase() {
    private val db = "merge_test_db"
    private val syncTable = "merge_sync_edge"

    // Schema: "required" (long, non-nullable), "optional" (string, nullable)
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
              {"name": "required", "type": "long",   "comment": "required", "nullable": false},
              {"name": "optional", "type": "string", "comment": "optional", "nullable": true}
            ],
            "direction": "OUT",
            "indexes": [],
            "groups": []
          },
          "storage": "$storage",
          "mode": "$mode",
          "comment": "$mode edge for insert-merge test"
        }
        """.trimIndent()

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "insert-merge test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(tableDdl(syncTable, "datastore://test_namespace/merge_sync_edge", "SYNC"))
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

    @Test
    fun `sync INSERT missing non-nullable field on an empty row is reported as INVALID`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"optional":"hello"}}}]}""")
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("INVALID")
    }

    @Test
    fun `sync INSERT with explicit null for non-nullable field still returns 400`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":1,"source":"A","target":"B","properties":{"required":null}}}]}""")
            .isBadRequest
    }

    @Test
    fun `sync partial INSERTs merge into one row (fan-in)`() {
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":1,"source":"C","target":"D","properties":{"required":7}}}]}""")
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")

        // Omits the non-nullable "required" — legal under merge, the existing value is kept.
        mutateSync("""{"mutations":[{"type":"INSERT","edge":{"version":2,"source":"C","target":"D","properties":{"optional":"hello"}}}]}""")
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("UPDATED")
    }
}
