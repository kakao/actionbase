package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AliasControllerTest : E2ETestBase() {
    private val db = "v3-alias-test-db"
    private val table = "v3-alias-target-table"
    private val baseUri = "/graph/v3/databases/$db/aliases"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test db"}""")
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
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CrudLifecycleTest {
        @Order(1)
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
        fun `create alias`(
            name: String,
            create: String,
            expected: String,
        ) {
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

            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @Order(2)
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-alias-basic
              update: |
                {"comment": "updated comment"}
              expected: |
                {"alias": "v3-alias-basic", "comment": "updated comment", "active": true}
            - name: v3-alias-empty
              update: |
                {"comment": "updated empty"}
              expected: |
                {"alias": "v3-alias-empty", "comment": "updated empty", "active": true}
            - name: v3-alias-special
              update: |
                {"comment": "updated special"}
              expected: |
                {"alias": "v3-alias-special", "comment": "updated special", "active": true}
            """,
        )
        fun `update alias`(
            name: String,
            update: String,
            expected: String,
        ) {
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @Order(3)
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-alias-basic
              deactivate: |
                {"active": false}
              expected: |
                {"alias": "v3-alias-basic", "active": false}
            - name: v3-alias-empty
              deactivate: |
                {"active": false}
              expected: |
                {"alias": "v3-alias-empty", "active": false}
            - name: v3-alias-special
              deactivate: |
                {"active": false}
              expected: |
                {"alias": "v3-alias-special", "active": false}
            """,
        )
        fun `deactivate alias`(
            name: String,
            deactivate: String,
            expected: String,
        ) {
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @Order(4)
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-alias-basic
            - name: v3-alias-empty
            - name: v3-alias-special
            """,
        )
        fun `delete alias`(name: String) {
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
