package com.kakao.actionbase.server

import com.kakao.actionbase.server.test.E2ETestBase

import kotlin.test.Test

import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["actionbase.read-only=true"])
class ReadOnlyModeStartUpTest : E2ETestBase() {
    @Test
    fun `server boots in read-only mode`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `GET requests are allowed in read-only mode`() {
        client
            .get()
            .uri("/graph/v3")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `POST requests are blocked in read-only mode`() {
        client
            .post()
            .uri("/graph/v3/databases")
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `PUT requests are blocked in read-only mode`() {
        client
            .put()
            .uri("/graph/v3/databases/db/tables/t")
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `DELETE requests are blocked in read-only mode`() {
        client
            .delete()
            .uri("/graph/v2/admin/service/test")
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @Test
    fun `POST to v2 query endpoint is allowed in read-only mode`() {
        client
            .post()
            .uri("/graph/v2/query")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for read-only POST query, got $status" }
            }
    }

    @Test
    fun `POST to v3 query endpoint is allowed in read-only mode`() {
        client
            .post()
            .uri("/graph/v3/query")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for read-only POST query, got $status" }
            }
    }

    @Test
    fun `POST to edges-get endpoint is allowed in read-only mode`() {
        client
            .post()
            .uri("/graph/v3/databases/db/tables/t/edges/get")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for read-only POST edges/get, got $status" }
            }
    }

    @Test
    fun `POST to non-graph paths is allowed in read-only mode`() {
        client
            .post()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for non-graph path, got $status" }
            }
    }
}
