package com.kakao.actionbase.server

import com.kakao.actionbase.server.test.E2ETestBase

import kotlin.test.Test

import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["actionbase.read-only=false"])
class ReadOnlyModeDisabledStartUpTest : E2ETestBase() {
    @Test
    fun `server boots with read-only disabled`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `POST requests are not blocked when read-only is disabled`() {
        client
            .post()
            .uri("/graph/v3/databases")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 when read-only is disabled, got $status" }
            }
    }

    @Test
    fun `PUT requests are not blocked when read-only is disabled`() {
        client
            .put()
            .uri("/graph/v3/databases/db/tables/t")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 when read-only is disabled, got $status" }
            }
    }

    @Test
    fun `DELETE requests are not blocked when read-only is disabled`() {
        client
            .delete()
            .uri("/graph/v2/admin/service/test")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 when read-only is disabled, got $status" }
            }
    }
}
