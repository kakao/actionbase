package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeMutationStorageOpsSpec : E2ETestBase() {
    private val db = "storage-ops-db"
    private val edgeTable = "storage-ops-edge"
    private val multiEdgeTable = "storage-ops-multi"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "storage ops test db"}""")
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
                  "table": "$edgeTable",
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
                  "storage": "datastore://test_namespace/storage_ops_edge",
                  "mode": "SYNC",
                  "comment": "edge for storage-ops test"
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
                  "storage": "datastore://test_namespace/storage_ops_multi",
                  "mode": "SYNC",
                  "comment": "multi edge for storage-ops test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: edge without header omits storage_ops
          table: storage-ops-edge
          path: edges
          header: null
          body: |
            {"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "a", "target": "b", "properties": {"score": 1}}}]}
          storageOpsExists: false

        - name: edge with header exposes storage_ops
          table: storage-ops-edge
          path: edges
          header: "true"
          body: |
            {"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "x", "target": "y", "properties": {"score": 2}}}]}
          storageOpsExists: true

        - name: multi-edge with header exposes storage_ops
          table: storage-ops-multi
          path: multi-edges
          header: "true"
          body: |
            {"mutations": [{"type": "INSERT", "edge": {"version": 1, "id": 42, "source": 1, "target": 2, "properties": {"score": 3}}}]}
          storageOpsExists: true
        """,
    )
    fun `storage_ops visibility gated by header`(
        table: String,
        path: String,
        header: String?,
        body: String,
        storageOpsExists: Boolean,
    ) {
        val request =
            client
                .post()
                .uri("/graph/v3/databases/$db/tables/$table/$path")
                .contentType(MediaType.APPLICATION_JSON)
        if (header != null) request.header("AB-Include-Mutation-Context", header)

        val result =
            request
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.results[0].status")
                .isEqualTo("CREATED")

        if (storageOpsExists) {
            result
                .jsonPath("$.results[0].context.storageOps[0].kind")
                .isEqualTo("Put")
                .jsonPath("$.results[0].context.storageOps[0].table")
                .isEqualTo("edges")
                .jsonPath("$.results[0].context.storageOps[0].cells[0].family")
                .isEqualTo("Zg==")
                .jsonPath("$.results[0].context.storageOps[0].cells[0].qualifier")
                .isEqualTo("ZQ==")
                .jsonPath("$.results[0].context.storageOps[0].row")
                .exists()
                .jsonPath("$.results[0].context.storageOps[0].cells[0].value")
                .exists()
        } else {
            result.jsonPath("$.results[0].context.storageOps").doesNotExist()
        }
    }
}
