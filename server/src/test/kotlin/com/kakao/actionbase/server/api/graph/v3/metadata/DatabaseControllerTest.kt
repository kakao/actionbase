package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DatabaseControllerTest : E2ETestBase() {
    private val testDatabase = "v3-test-db"

    @Test
    @Order(1)
    fun `list databases`() {
        client
            .get()
            .uri("/graph/v3/databases")
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList(DatabaseDescriptor::class.java)
    }

    @Test
    @Order(2)
    fun `create database`() {
        val request =
            DatabaseCreateRequest(
                database = testDatabase,
                comment = "test database for v3 api",
            )

        client
            .post()
            .uri("/graph/v3/databases/$testDatabase")
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(DatabaseDescriptor::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
                assert(body.comment == "test database for v3 api")
            }
    }

    @Test
    @Order(3)
    fun `get database`() {
        client
            .get()
            .uri("/graph/v3/databases/$testDatabase")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(DatabaseDescriptor::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.database == testDatabase)
            }
    }

    @Test
    @Order(4)
    fun `get non-existent database returns 404`() {
        client
            .get()
            .uri("/graph/v3/databases/non-existent-db")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
