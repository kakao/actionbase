package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.configuration.HttpHeaderConstants.INCLUDE_MUTATION_CONTEXT
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutationContextOptInE2ETest : E2ETestBase() {
    private val db = "ctx_optin_db"
    private val edgeTable = "ctx_optin_edge"
    private val multiEdgeTable = "ctx_optin_multi_edge"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "context opt-in test"}""")
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
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/ctx_optin_edge",
                  "mode": "SYNC",
                  "comment": "edge for context opt-in test"
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
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/ctx_optin_multi_edge",
                  "mode": "SYNC",
                  "comment": "multi-edge for context opt-in test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `edge mutation without header omits context`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/sync")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "s1", "target": "t1", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
            .jsonPath("$.results[0].context")
            .doesNotExist()
    }

    @Test
    fun `edge mutation with header emits empty context`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/sync")
            .header(INCLUDE_MUTATION_CONTEXT, "true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "s2", "target": "t2", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
            .jsonPath("$.results[0].context")
            .isMap
    }

    @Test
    fun `multi-edge mutation with header emits empty context`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$multiEdgeTable/multi-edges/sync")
            .header(INCLUDE_MUTATION_CONTEXT, "true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "id": 77777, "source": 1, "target": 2, "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
            .jsonPath("$.results[0].context")
            .isMap
    }

    @Test
    fun `edge mutation with header false omits context`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/sync")
            .header(INCLUDE_MUTATION_CONTEXT, "false")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "s-false", "target": "t-false", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].context")
            .doesNotExist()
    }

    @Test
    fun `V3 async edge mutation with header emits empty context`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges")
            .header(INCLUDE_MUTATION_CONTEXT, "true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "s-async", "target": "t-async", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].context")
            .isMap
    }

    @Test
    fun `header is case-insensitive`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/sync")
            .header(INCLUDE_MUTATION_CONTEXT, "TRUE")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"mutations": [{"type": "INSERT", "edge": {"version": 1, "source": "s3", "target": "t3", "properties": {}}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.results[0].context")
            .isMap
    }

    @Test
    fun `V2 edge mutation without header omits context`() {
        client
            .post()
            .uri("/graph/v2/edge")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"label": "$db.$edgeTable", "edges": [{"ts": 1, "src": "s-v2-a", "tgt": "t-v2-a", "props": {}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.result[0].context")
            .doesNotExist()
    }

    @Test
    fun `V2 edge mutation with header emits empty context`() {
        client
            .post()
            .uri("/graph/v2/edge")
            .header(INCLUDE_MUTATION_CONTEXT, "true")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"label": "$db.$edgeTable", "edges": [{"ts": 1, "src": "s-v2-b", "tgt": "t-v2-b", "props": {}}]}""",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.result[0].context")
            .isMap
    }
}
