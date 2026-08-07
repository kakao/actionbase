package com.kakao.actionbase.server.api.control.cleanup

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * The cleanup view over http. Both configured clusters are closed ports, so every side fails - the
 * case that must not read as "nothing left to clean up".
 */
@SpringBootTest(
    properties = [
        "actionbase.role=CONTROL",
        "actionbase.control.request-timeout=2s",
        "actionbase.control.tenants.alpha.env=prod",
        "actionbase.control.tenants.alpha.namespace=ab_alpha",
        "actionbase.control.tenants.alpha.active-url=http://127.0.0.1:1",
        "actionbase.control.tenants.alpha.standby-url=http://127.0.0.1:2",
        "actionbase.control.tenants.stg.env=test",
        "actionbase.control.tenants.stg.namespace=ab_stg",
        "actionbase.control.tenants.stg.active-url=http://127.0.0.1:3",
    ],
)
class ControlCleanupE2ETest : E2ETestBase() {
    @Test
    fun `every unreachable side is reported as a failure`() {
        client
            .get()
            .uri("/control/htables")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.tables.length()")
            .isEqualTo(0)
            // alpha active, alpha standby, stg active
            .jsonPath("$.failures.length()")
            .isEqualTo(3)
            .jsonPath("$.failures[0].tenant")
            .isEqualTo("alpha")
            .jsonPath("$.failures[0].error")
            .exists()
    }

    @Test
    fun `a tenant filter narrows the fleet`() {
        client
            .get()
            .uri("/control/htables?tenant=stg")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.failures.length()")
            .isEqualTo(1)
            .jsonPath("$.failures[0].tenant")
            .isEqualTo("stg")
    }

    @Test
    fun `an env filter narrows the fleet`() {
        client
            .get()
            .uri("/control/htables?env=prod")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.failures.length()")
            .isEqualTo(2)
    }

    @Test
    fun `an unknown env names the ones that exist`() {
        client
            .get()
            .uri("/control/htables?env=staging")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { assertTrue(it.contains("dev"), it) }
    }

    @Test
    fun `an unknown tenant is a bad request`() {
        client
            .get()
            .uri("/control/htables?tenant=nope")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
