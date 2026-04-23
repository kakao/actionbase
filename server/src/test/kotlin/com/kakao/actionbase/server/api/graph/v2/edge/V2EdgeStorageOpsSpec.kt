package com.kakao.actionbase.server.api.graph.v2.edge

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2EdgeStorageOpsSpec : E2ETestBase() {
    private val service = "v2-storage-ops-svc"
    private val label = "v2-storage-ops-label"
    private val name = "$service.$label"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$service", "comment": "v2 storage ops test service"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$service/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$label",
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
                  "storage": "datastore://test_namespace/v2_storage_ops_label",
                  "mode": "SYNC",
                  "comment": "v2 storage ops label"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: v2 edge without header omits storage_ops
          header: null
          edge: |
            {"ts": 1, "src": "a", "tgt": "b", "props": {"score": 1}}
          storageOpsExists: false

        - name: v2 edge with header exposes storage_ops
          header: "true"
          edge: |
            {"ts": 1, "src": "x", "tgt": "y", "props": {"score": 2}}
          storageOpsExists: true
        """,
    )
    fun `storage_ops visibility gated by header`(
        header: String?,
        edge: String,
        storageOpsExists: Boolean,
    ) {
        val request =
            client
                .post()
                .uri("/graph/v2/edge")
                .contentType(MediaType.APPLICATION_JSON)
        if (header != null) request.header("AB-Include-Mutation-Context", header)

        val result =
            request
                .bodyValue("""{"label": "$name", "edges": [$edge]}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.result[0].status")
                .isEqualTo("CREATED")

        if (storageOpsExists) {
            result
                .jsonPath("$.result[0].context.storageOps[0].kind")
                .isEqualTo("Put")
                .jsonPath("$.result[0].context.storageOps[0].table")
                .isEqualTo("edges")
                .jsonPath("$.result[0].context.storageOps[0].cells[0].family")
                .isEqualTo("Zg==")
                .jsonPath("$.result[0].context.storageOps[0].cells[0].qualifier")
                .isEqualTo("ZQ==")
                .jsonPath("$.result[0].context.storageOps[0].row")
                .exists()
                .jsonPath("$.result[0].context.storageOps[0].cells[0].value")
                .exists()
        } else {
            result.jsonPath("$.result[0].context.storageOps").doesNotExist()
        }
    }
}
