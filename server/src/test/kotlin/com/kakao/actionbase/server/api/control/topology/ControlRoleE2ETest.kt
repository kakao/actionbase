package com.kakao.actionbase.server.api.control.topology

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** An instance told to be a control plane serves `/control`. */
@SpringBootTest(
    properties = [
        "actionbase.role=CONTROL",
        "actionbase.control.tenants.alpha.env=prod",
        "actionbase.control.tenants.alpha.namespace=ab_alpha",
        "actionbase.control.tenants.alpha.active-url=http://ab-alpha.example.net",
        "actionbase.control.tenants.alpha.standby-url=http://ab-alpha-standby.example.net",
        "actionbase.control.tenants.beta.env=prod",
        "actionbase.control.tenants.beta.namespace=ab_beta",
        "actionbase.control.tenants.beta.active-url=http://ab-beta.example.net",
    ],
)
class ControlRoleE2ETest : E2ETestBase() {
    @Test
    fun `lists the configured tenants and their sides`() {
        client
            .get()
            .uri("/control/topology")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.tenants.length()")
            .isEqualTo(2)
            .jsonPath("$.tenants[0].tenant")
            .isEqualTo("alpha")
            .jsonPath("$.tenants[0].sides")
            .isEqualTo(listOf("ACTIVE", "STANDBY"))
            .jsonPath("$.tenants[1].tenant")
            .isEqualTo("beta")
            .jsonPath("$.tenants[1].sides")
            .isEqualTo(listOf("ACTIVE"))
    }

    @Test
    fun `answers for a single tenant`() {
        client
            .get()
            .uri("/control/topology/beta")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.namespace")
            .isEqualTo("ab_beta")
            .jsonPath("$.env")
            .isEqualTo("PROD")
    }

    @Test
    fun `does not serve the data plane`() {
        client
            .get()
            .uri("/graph/v3/databases")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `still answers the probes`() {
        client
            .get()
            .uri("/graph/health/liveness")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `an unknown tenant is a bad request, not a server error`() {
        client
            .get()
            .uri("/control/topology/nope")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { assertTrue(it.contains("[alpha, beta]"), it) }
    }
}
