package com.kakao.actionbase.server

import com.kakao.actionbase.server.test.E2ETestBase

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
