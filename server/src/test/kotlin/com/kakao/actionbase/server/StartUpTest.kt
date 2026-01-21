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

@TestPropertySource(properties = ["kc.graph.global-mutation-mode=SYNC"])
class StartUpWithGlobalMutationModeSyncTest : E2ETestBase() {
    @Test
    fun `server boots with globalMutationMode=SYNC`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }
}

@TestPropertySource(properties = ["kc.graph.global-mutation-mode=ASYNC"])
class StartUpWithGlobalMutationModeAsyncTest : E2ETestBase() {
    @Test
    fun `server boots with globalMutationMode=ASYNC`() {
        client
            .get()
            .uri("/graph/v2")
            .exchange()
            .expectStatus()
            .isOk
    }
}
