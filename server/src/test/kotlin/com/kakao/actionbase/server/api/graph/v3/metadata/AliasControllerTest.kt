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
    private val db = "v3_alias_test_db"
    private val table = "v3_alias_target_table"
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
            - name: v3_alias_basic
              create: |
                {"alias": "v3_alias_basic", "table": "v3_alias_target_table", "comment": "test alias"}
              expected: |
                {"alias": "v3_alias_basic", "table": "v3_alias_target_table", "comment": "test alias", "active": true}
            - name: v3_alias_empty
              create: |
                {"alias": "v3_alias_empty", "table": "v3_alias_target_table", "comment": ""}
              expected: |
                {"alias": "v3_alias_empty", "table": "v3_alias_target_table", "comment": "", "active": true}
            - name: v3_alias_special
              create: |
                {"alias": "v3_alias_special", "table": "v3_alias_target_table", "comment": "alias @#"}
              expected: |
                {"alias": "v3_alias_special", "table": "v3_alias_target_table", "comment": "alias @#", "active": true}
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
            - name: v3_alias_upd_basic
              create: |
                {"alias": "v3_alias_upd_basic", "table": "v3_alias_target_table", "comment": "test alias"}
              update: |
                {"comment": "updated comment"}
              expected: |
                {"alias": "v3_alias_upd_basic", "comment": "updated comment", "active": true}
            - name: v3_alias_upd_empty
              create: |
                {"alias": "v3_alias_upd_empty", "table": "v3_alias_target_table", "comment": ""}
              update: |
                {"comment": "updated empty"}
              expected: |
                {"alias": "v3_alias_upd_empty", "comment": "updated empty", "active": true}
            - name: v3_alias_upd_special
              create: |
                {"alias": "v3_alias_upd_special", "table": "v3_alias_target_table", "comment": "alias @#"}
              update: |
                {"comment": "updated special"}
              expected: |
                {"alias": "v3_alias_upd_special", "comment": "updated special", "active": true}
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
            - name: v3_alias_deact_basic
              create: |
                {"alias": "v3_alias_deact_basic", "table": "v3_alias_target_table", "comment": "test alias"}
              expected: |
                {"alias": "v3_alias_deact_basic", "active": false}
            - name: v3_alias_deact_empty
              create: |
                {"alias": "v3_alias_deact_empty", "table": "v3_alias_target_table", "comment": ""}
              expected: |
                {"alias": "v3_alias_deact_empty", "active": false}
            - name: v3_alias_deact_special
              create: |
                {"alias": "v3_alias_deact_special", "table": "v3_alias_target_table", "comment": "alias @#"}
              expected: |
                {"alias": "v3_alias_deact_special", "active": false}
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
            - name: v3_alias_react_basic
              create: |
                {"alias": "v3_alias_react_basic", "table": "v3_alias_target_table", "comment": "test alias"}
              expected: |
                {"alias": "v3_alias_react_basic", "active": true}
            - name: v3_alias_react_empty
              create: |
                {"alias": "v3_alias_react_empty", "table": "v3_alias_target_table", "comment": ""}
              expected: |
                {"alias": "v3_alias_react_empty", "active": true}
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
            - name: v3_alias_del_basic
              create: |
                {"alias": "v3_alias_del_basic", "table": "v3_alias_target_table", "comment": "test alias"}
            - name: v3_alias_del_empty
              create: |
                {"alias": "v3_alias_del_empty", "table": "v3_alias_target_table", "comment": ""}
            - name: v3_alias_del_special
              create: |
                {"alias": "v3_alias_del_special", "table": "v3_alias_target_table", "comment": "alias @#"}
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class StatusFilterTest {
        private val aliasName = "v3_alias_status_filter"

        @BeforeAll
        fun setup() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"alias": "$aliasName", "table": "$table", "comment": "status filter test"}""")
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$aliasName")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": false}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `default status excludes inactive aliases`() {
            client
                .get()
                .uri(baseUri)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.alias == '$aliasName')]")
                .doesNotExist()
        }

        @Test
        fun `status=ACTIVE excludes inactive aliases`() {
            client
                .get()
                .uri("$baseUri?status=ACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.alias == '$aliasName')]")
                .doesNotExist()
        }

        @Test
        fun `status=INACTIVE returns only inactive aliases`() {
            client
                .get()
                .uri("$baseUri?status=INACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.alias == '$aliasName')]")
                .exists()
        }

        @Test
        fun `status=ALL returns both active and inactive aliases`() {
            client
                .get()
                .uri("$baseUri?status=ALL")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.alias == '$aliasName')]")
                .exists()
        }
    }

    @Nested
    inner class ValidationTest {
        @Test
        fun `invalid status value returns 400`() {
            client
                .get()
                .uri("$baseUri?status=BOGUS")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `lowercase status value returns 400`() {
            client
                .get()
                .uri("$baseUri?status=active")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `get non-existent alias returns 404`() {
            client
                .get()
                .uri("$baseUri/non_existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid alias name returns 400`() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"alias": "123invalid", "table": "valid_table", "comment": ""}""")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `alias name with dot returns 400`() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"alias": "alias.injection", "table": "valid_table", "comment": ""}""")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
