package com.kakao.actionbase.server.api.control.cleanup

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/** The job endpoint over http, including the refusal of anything that would take effect. */
@SpringBootTest(
    properties = [
        "actionbase.role=CONTROL",
        "actionbase.control.request-timeout=2s",
        "actionbase.control.tenants.alpha.env=prod",
        "actionbase.control.tenants.alpha.namespace=ab_alpha",
        "actionbase.control.tenants.alpha.active-url=http://127.0.0.1:1",
    ],
)
class ControlJobE2ETest : E2ETestBase() {
    @Test
    fun `refuses to execute until authorization is in place`() {
        client
            .post()
            .uri("/control/jobs")
            .bodyValue(mapOf("targets" to listOf(target()), "action" to "drop_table", "dryRun" to false))
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { assertTrue(it.contains("dryRun"), it) }
    }

    @Test
    fun `plans a dry run and reports the cluster it could not read`() {
        client
            .post()
            .uri("/control/jobs")
            .bodyValue(mapOf("targets" to listOf(target()), "action" to "drop_table", "dryRun" to true))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.dryRun")
            .isEqualTo(true)
            .jsonPath("$.action")
            .isEqualTo("DROP_TABLE")
            .jsonPath("$.plans.length()")
            .isEqualTo(1)
            // The cluster never answered, so the table was not seen - and the failure says why.
            .jsonPath("$.plans[0].ok")
            .isEqualTo(false)
            .jsonPath("$.plans[0].refusal")
            .isEqualTo("TABLE_ALREADY_GONE")
            .jsonPath("$.failures.length()")
            .isEqualTo(1)
    }

    @Test
    fun `an unknown action names the ones that exist`() {
        client
            .post()
            .uri("/control/jobs")
            .bodyValue(mapOf("targets" to listOf(target()), "action" to "obliterate"))
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { assertTrue(it.contains("delete_metadata"), it) }
    }

    @Test
    fun `an empty target list is a bad request`() {
        client
            .post()
            .uri("/control/jobs")
            .bodyValue(mapOf("targets" to emptyList<Any>(), "action" to "drop_table"))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    private fun target() = mapOf("tenant" to "alpha", "table" to "ab_alpha:t1")
}
