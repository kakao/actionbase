package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["kc.graph.system-mutation-mode=ASYNC"])
class EdgeMutationForceSyncTest : E2ETestBase() {
    private val db = "force-sync-test-db"
    private val table = "force-sync-edge"
    private val multiEdgeTable = "force-sync-multi-edge"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "force sync test db"}""")
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
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "long", "comment": "score"}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/force_sync_edge",
                  "mode": "SYNC",
                  "comment": "sync edge for force sync test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$multiEdgeTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "id"},
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "long", "comment": "score"}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/force_sync_multi_edge",
                  "mode": "SYNC",
                  "comment": "sync multi edge for force sync test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `sync endpoint queues when system=ASYNC overrides`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "1", "target": "2", "properties": {"score": 10}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("QUEUED")
    }

    @Test
    fun `sync endpoint with force=true overrides system=ASYNC on EDGE table`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges/sync?force=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "100", "target": "200", "properties": {"score": 10}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
    }

    @Test
    fun `sync endpoint with force=true overrides system=ASYNC on MULTI_EDGE table`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$multiEdgeTable/multi-edges/sync?force=true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "id": 99999, "source": 1, "target": 2, "properties": {"score": 10}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
    }
}
