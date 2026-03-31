package com.kakao.actionbase.server

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.Test

import org.springframework.test.context.TestPropertySource

class StartUpTest : E2ETestBase() {
    @Test
    fun checkV2() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun checkV3() {
        client
            .get()
            .uri("/graph/v3")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `POST is not rejected as 403 when read-only is not configured`() {
        client
            .post()
            .uri("/graph/v3/databases")
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403, got $status" }
            }
    }
}

@TestPropertySource(properties = ["kc.graph.system-mutation-mode=SYNC"])
class StartUpWithSystemMutationModeSyncTest : E2ETestBase() {
    @Test
    fun `server boots with systemMutationMode=SYNC`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }
}

@TestPropertySource(properties = ["kc.graph.system-mutation-mode=ASYNC"])
class StartUpWithSystemMutationModeAsyncTest : E2ETestBase() {
    @Test
    fun `server boots with systemMutationMode=ASYNC`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }
}

@TestPropertySource(properties = ["actionbase.read-only=true"])
class StartUpWithReadOnlyEnabledTest : E2ETestBase() {
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: POST
          uri: /graph/v3/databases
        - method: PUT
          uri: /graph/v3/databases/db/tables/t
        - method: DELETE
          uri: /graph/v2/admin/service/test
        """,
    )
    fun `should block write requests`(
        method: String,
        uri: String,
    ) {
        client
            .method(
                org.springframework.http.HttpMethod
                    .valueOf(method),
            ).uri(uri)
            .exchange()
            .expectStatus()
            .isForbidden
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: GET
          uri: /graph/v2
        - method: GET
          uri: /graph/v3
        - method: POST
          uri: /graph/v2/query
        - method: POST
          uri: /graph/v3/query
        - method: POST
          uri: /graph/v3/databases/db/tables/t/edges/get
        - method: POST
          uri: /actuator/health
        """,
    )
    fun `should allow read requests`(
        method: String,
        uri: String,
    ) {
        client
            .method(
                org.springframework.http.HttpMethod
                    .valueOf(method),
            ).uri(uri)
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for $method $uri, got $status" }
            }
    }
}

@TestPropertySource(properties = ["actionbase.read-only=false"])
class StartUpWithReadOnlyDisabledTest : E2ETestBase() {
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - method: GET
          uri: /graph/v2/service
        - method: GET
          uri: /graph/v3/databases
        - method: POST
          uri: /graph/v3/databases
        - method: PUT
          uri: /graph/v3/databases/db/tables/t
        - method: DELETE
          uri: /graph/v2/admin/service/test
        """,
    )
    fun `no request is blocked when read-only is disabled`(
        method: String,
        uri: String,
    ) {
        client
            .method(
                org.springframework.http.HttpMethod
                    .valueOf(method),
            ).uri(uri)
            .exchange()
            .expectStatus()
            .value { status ->
                assert(status != 403) { "Expected non-403 for $method $uri, got $status" }
            }
    }
}
