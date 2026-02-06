package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

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
            - name: v3-db-basic
              create: |
                {"database": "v3-db-basic", "comment": "test db"}
              expected: |
                {"database": "v3-db-basic", "comment": "test db", "active": true}
            - name: v3-db-empty-comment
              create: |
                {"database": "v3-db-empty-comment", "comment": ""}
              expected: |
                {"database": "v3-db-empty-comment", "comment": "", "active": true}
            - name: v3-db-special
              create: |
                {"database": "v3-db-special", "comment": "test @#$%"}
              expected: |
                {"database": "v3-db-special", "comment": "test @#$%", "active": true}
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
            - name: v3-db-upd-basic
              create: |
                {"database": "v3-db-upd-basic", "comment": "test db"}
              update: |
                {"comment": "updated comment"}
              expected: |
                {"database": "v3-db-upd-basic", "comment": "updated comment", "active": true}
            - name: v3-db-upd-empty
              create: |
                {"database": "v3-db-upd-empty", "comment": ""}
              update: |
                {"comment": "updated empty"}
              expected: |
                {"database": "v3-db-upd-empty", "comment": "updated empty", "active": true}
            - name: v3-db-upd-special
              create: |
                {"database": "v3-db-upd-special", "comment": "test @#$%"}
              update: |
                {"comment": "updated special"}
              expected: |
                {"database": "v3-db-upd-special", "comment": "updated special", "active": true}
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
            - name: v3-db-deact-basic
              create: |
                {"database": "v3-db-deact-basic", "comment": "test db"}
              expected: |
                {"database": "v3-db-deact-basic", "active": false}
            - name: v3-db-deact-empty
              create: |
                {"database": "v3-db-deact-empty", "comment": ""}
              expected: |
                {"database": "v3-db-deact-empty", "active": false}
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
            - name: v3-db-react-basic
              create: |
                {"database": "v3-db-react-basic", "comment": "test db"}
              expected: |
                {"database": "v3-db-react-basic", "active": true}
            - name: v3-db-react-empty
              create: |
                {"database": "v3-db-react-empty", "comment": ""}
              expected: |
                {"database": "v3-db-react-empty", "active": true}
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
            - name: v3-db-del-basic
              create: |
                {"database": "v3-db-del-basic", "comment": "test db"}
            - name: v3-db-del-empty
              create: |
                {"database": "v3-db-del-empty", "comment": ""}
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
    inner class ValidationTest {
        @Test
        fun `get non-existent database returns 404`() {
            client
                .get()
                .uri("/graph/v3/databases/non-existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid database name returns 400`() {
            client
                .get()
                .uri("/graph/v3/databases/123-invalid")
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
