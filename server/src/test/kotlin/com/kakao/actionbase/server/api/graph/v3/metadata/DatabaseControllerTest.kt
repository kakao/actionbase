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
class DatabaseControllerTest : E2ETestBase() {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CrudTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3_db_basic
              create: |
                {"database": "v3_db_basic", "comment": "test db"}
              expected: |
                {"database": "v3_db_basic", "comment": "test db", "active": true}
            - name: v3_db_empty_comment
              create: |
                {"database": "v3_db_empty_comment", "comment": ""}
              expected: |
                {"database": "v3_db_empty_comment", "comment": "", "active": true}
            - name: v3_db_special
              create: |
                {"database": "v3_db_special", "comment": "test @#$%"}
              expected: |
                {"database": "v3_db_special", "comment": "test @#$%", "active": true}
            """,
        )
        fun `create database`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            client
                .get()
                .uri("/graph/v3/databases/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3_db_upd_basic
              create: |
                {"database": "v3_db_upd_basic", "comment": "test db"}
              update: |
                {"comment": "updated comment"}
              expected: |
                {"database": "v3_db_upd_basic", "comment": "updated comment", "active": true}
            - name: v3_db_upd_empty
              create: |
                {"database": "v3_db_upd_empty", "comment": ""}
              update: |
                {"comment": "updated empty"}
              expected: |
                {"database": "v3_db_upd_empty", "comment": "updated empty", "active": true}
            - name: v3_db_upd_special
              create: |
                {"database": "v3_db_upd_special", "comment": "test @#$%"}
              update: |
                {"comment": "updated special"}
              expected: |
                {"database": "v3_db_upd_special", "comment": "updated special", "active": true}
            """,
        )
        fun `update database`(
            name: String,
            create: String,
            update: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$name")
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
            - name: v3_db_deact_basic
              create: |
                {"database": "v3_db_deact_basic", "comment": "test db"}
              expected: |
                {"database": "v3_db_deact_basic", "active": false}
            - name: v3_db_deact_empty
              create: |
                {"database": "v3_db_deact_empty", "comment": ""}
              expected: |
                {"database": "v3_db_deact_empty", "active": false}
            """,
        )
        fun `deactivate database`(
            name: String,
            create: String,
            deactivate: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$name")
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
            - name: v3_db_react_basic
              create: |
                {"database": "v3_db_react_basic", "comment": "test db"}
              expected: |
                {"database": "v3_db_react_basic", "active": true}
            - name: v3_db_react_empty
              create: |
                {"database": "v3_db_react_empty", "comment": ""}
              expected: |
                {"database": "v3_db_react_empty", "active": true}
            """,
        )
        fun `reactivate database`(
            name: String,
            create: String,
            deactivate: String,
            reactivate: String,
            expected: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$name")
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
            - name: v3_db_del_basic
              create: |
                {"database": "v3_db_del_basic", "comment": "test db"}
            - name: v3_db_del_empty
              create: |
                {"database": "v3_db_del_empty", "comment": ""}
            """,
        )
        fun `delete database`(
            name: String,
            create: String,
            deactivate: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

            client
                .delete()
                .uri("/graph/v3/databases/$name")
                .exchange()
                .expectStatus()
                .isNoContent
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class StatusFilterTest {
        private val dbName = "v3_db_status_filter"

        @BeforeAll
        fun setup() {
            // create and deactivate a database
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$dbName", "comment": "status filter test"}""")
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("/graph/v3/databases/$dbName")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": false}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `default status excludes inactive databases`() {
            client
                .get()
                .uri("/graph/v3/databases")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.database == '$dbName')]")
                .doesNotExist()
        }

        @Test
        fun `status=ACTIVE excludes inactive databases`() {
            client
                .get()
                .uri("/graph/v3/databases?status=ACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.database == '$dbName')]")
                .doesNotExist()
        }

        @Test
        fun `status=INACTIVE returns only inactive databases`() {
            client
                .get()
                .uri("/graph/v3/databases?status=INACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.database == '$dbName')]")
                .exists()
        }

        @Test
        fun `status=ALL returns both active and inactive databases`() {
            client
                .get()
                .uri("/graph/v3/databases?status=ALL")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.database == '$dbName')]")
                .exists()
        }
    }

    @Nested
    inner class ValidationTest {
        @Test
        fun `invalid status value returns 400`() {
            client
                .get()
                .uri("/graph/v3/databases?status=BOGUS")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `lowercase status value returns 400`() {
            client
                .get()
                .uri("/graph/v3/databases?status=active")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `get non-existent database returns 404`() {
            client
                .get()
                .uri("/graph/v3/databases/non_existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid database name returns 400`() {
            client
                .get()
                .uri("/graph/v3/databases/123invalid")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `database name with dot returns 400`() {
            client
                .get()
                .uri("/graph/v3/databases/db.injection")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
