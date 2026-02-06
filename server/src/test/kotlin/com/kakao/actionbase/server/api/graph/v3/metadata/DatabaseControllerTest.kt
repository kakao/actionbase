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
    inner class CrudLifecycleTest {
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
        fun `create - get - update`(
            name: String,
            create: String,
            expected: String,
        ) {
            // Create
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

            // Get
            client
                .get()
                .uri("/graph/v3/databases/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // Update
            client
                .put()
                .uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated comment"}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"database": "$name", "comment": "updated comment", "active": true}""")
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
