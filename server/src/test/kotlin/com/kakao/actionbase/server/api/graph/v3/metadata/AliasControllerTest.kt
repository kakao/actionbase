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
    inner class CrudTest {
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

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-alias-upd-basic
              create: |
                {"alias": "v3-alias-upd-basic", "table": "v3-alias-target-table", "comment": "test alias"}
              update: |
                {"comment": "updated comment"}
              expected: |
                {"alias": "v3-alias-upd-basic", "comment": "updated comment", "active": true}
            - name: v3-alias-upd-empty
              create: |
                {"alias": "v3-alias-upd-empty", "table": "v3-alias-target-table", "comment": ""}
              update: |
                {"comment": "updated empty"}
              expected: |
                {"alias": "v3-alias-upd-empty", "comment": "updated empty", "active": true}
            - name: v3-alias-upd-special
              create: |
                {"alias": "v3-alias-upd-special", "table": "v3-alias-target-table", "comment": "alias @#"}
              update: |
                {"comment": "updated special"}
              expected: |
                {"alias": "v3-alias-upd-special", "comment": "updated special", "active": true}
            """,
        )
        fun `update alias`(
            name: String,
            create: String,
            update: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

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

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3-alias-deact-basic
              create: |
                {"alias": "v3-alias-deact-basic", "table": "v3-alias-target-table", "comment": "test alias"}
              expected: |
                {"alias": "v3-alias-deact-basic", "active": false}
            - name: v3-alias-deact-empty
              create: |
                {"alias": "v3-alias-deact-empty", "table": "v3-alias-target-table", "comment": ""}
              expected: |
                {"alias": "v3-alias-deact-empty", "active": false}
            - name: v3-alias-deact-special
              create: |
                {"alias": "v3-alias-deact-special", "table": "v3-alias-target-table", "comment": "alias @#"}
              expected: |
                {"alias": "v3-alias-deact-special", "active": false}
            """,
        )
        fun `deactivate alias`(
            name: String,
            create: String,
            deactivate: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

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

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
              reactivate: |
                {"active": true}
            """,
            cases = """
            - name: v3-alias-react-basic
              create: |
                {"alias": "v3-alias-react-basic", "table": "v3-alias-target-table", "comment": "test alias"}
              expected: |
                {"alias": "v3-alias-react-basic", "active": true}
            - name: v3-alias-react-empty
              create: |
                {"alias": "v3-alias-react-empty", "table": "v3-alias-target-table", "comment": ""}
              expected: |
                {"alias": "v3-alias-react-empty", "active": true}
            """,
        )
        fun `reactivate alias`(
            name: String,
            create: String,
            deactivate: String,
            reactivate: String,
            expected: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reactivate)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3-alias-del-basic
              create: |
                {"alias": "v3-alias-del-basic", "table": "v3-alias-target-table", "comment": "test alias"}
            - name: v3-alias-del-empty
              create: |
                {"alias": "v3-alias-del-empty", "table": "v3-alias-target-table", "comment": ""}
            - name: v3-alias-del-special
              create: |
                {"alias": "v3-alias-del-special", "table": "v3-alias-target-table", "comment": "alias @#"}
            """,
        )
        fun `delete alias`(
            name: String,
            create: String,
            deactivate: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

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
