package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AliasControllerTest : E2ETestBase() {
    private val db = "v3-alias-test-db"
    private val table = "v3-alias-target-table"
    private val baseUri = "/graph/v3/databases/$db/aliases"

    @BeforeAll
    fun setup() {
        // Create database
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        // Create table (alias target)
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
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/alias_test_hbase_table",
                  "mode": "SYNC",
                  "comment": "target table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CrudLifecycleTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-alias-basic
              create: |
                {"alias": "v3-alias-basic", "table": "v3-alias-target-table", "comment": "test alias"}
              expected: |
                {"alias": "v3-alias-basic", "table": "v3-alias-target-table", "comment": "test alias", "active": true}

            - name: v3-alias-empty
              create: |
                {"alias": "v3-alias-empty", "table": "v3-alias-target-table", "comment": ""}
              expected: |
                {"alias": "v3-alias-empty", "table": "v3-alias-target-table", "comment": "", "active": true}

            - name: v3-alias-special
              create: |
                {"alias": "v3-alias-special", "table": "v3-alias-target-table", "comment": "alias @#"}
              expected: |
                {"alias": "v3-alias-special", "table": "v3-alias-target-table", "comment": "alias @#", "active": true}
            """,
        )
        fun `create - get - update - deactivate - delete`(
            name: String,
            create: String,
            expected: String,
        ) {
            // Create
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // Get
            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // Update
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated comment"}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"alias": "$name", "comment": "updated comment", "active": true}""")

            // Deactivate
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": false}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"alias": "$name", "active": false}""")

            // Delete
            client
                .delete()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isNoContent
        }
    }

    @Nested
    inner class ValidationTest {
        @Test
        fun `get non-existent alias returns 404`() {
            client
                .get()
                .uri("$baseUri/non-existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid alias name returns 400`() {
            client
                .get()
                .uri("$baseUri/123-invalid")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `alias name with dot returns 400`() {
            client
                .get()
                .uri("$baseUri/alias.injection")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
