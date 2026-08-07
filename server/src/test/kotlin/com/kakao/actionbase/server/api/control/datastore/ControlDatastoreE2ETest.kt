package com.kakao.actionbase.server.api.control.datastore

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * The fanout endpoint over http. The configured cluster is a closed port, which is the interesting
 * case: an unreachable cluster is reported as that side failing, not as the request failing.
 */
@SpringBootTest(
    properties = [
        "actionbase.role=CONTROL",
        "actionbase.control.request-timeout=2s",
        "actionbase.control.tenants.alpha.env=prod",
        "actionbase.control.tenants.alpha.namespace=ab_alpha",
        "actionbase.control.tenants.alpha.active-url=http://127.0.0.1:1",
        "actionbase.control.tenants.alpha.standby-url=http://127.0.0.1:2",
    ],
)
class ControlDatastoreE2ETest : E2ETestBase() {
    @Test
    fun `reports an unreachable cluster as that side failing`() {
        client
            .get()
            .uri("/control/tenants/alpha/datastore/tables")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.tenant")
            .isEqualTo("alpha")
            .jsonPath("$.sides.length()")
            .isEqualTo(1)
            .jsonPath("$.sides[0].side")
            .isEqualTo("ACTIVE")
            .jsonPath("$.sides[0].ok")
            .isEqualTo(false)
            .jsonPath("$.sides[0].error")
            .exists()
    }

    @Test
    fun `fanout reaches both sides of a paired tenant`() {
        client
            .get()
            .uri("/control/tenants/alpha/datastore/tables?fanout=true")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.sides.length()")
            .isEqualTo(2)
            .jsonPath("$.sides[0].side")
            .isEqualTo("ACTIVE")
            .jsonPath("$.sides[1].side")
            .isEqualTo("STANDBY")
    }

    @Test
    fun `an unknown tenant is a bad request`() {
        client
            .get()
            .uri("/control/tenants/nope/datastore/tables")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
