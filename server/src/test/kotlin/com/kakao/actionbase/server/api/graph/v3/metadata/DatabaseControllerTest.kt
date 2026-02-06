package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseControllerTest : E2ETestBase() {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CrudLifecycleTest {
        @Order(1)
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

        @Order(2)
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-db-basic
              update: |
                {"comment": "updated comment"}
              expected: |
                {"database": "v3-db-basic", "comment": "updated comment", "active": true}
            - name: v3-db-empty-comment
              update: |
                {"comment": "updated empty"}
              expected: |
                {"database": "v3-db-empty-comment", "comment": "updated empty", "active": true}
            - name: v3-db-special
              update: |
                {"comment": "updated special"}
              expected: |
                {"database": "v3-db-special", "comment": "updated special", "active": true}
            """,
        )
        fun `update database`(
            name: String,
            update: String,
            expected: String,
        ) {
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
