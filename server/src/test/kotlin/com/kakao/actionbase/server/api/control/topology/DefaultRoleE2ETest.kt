package com.kakao.actionbase.server.api.control.topology

import com.kakao.actionbase.server.configuration.ServerProperties
import com.kakao.actionbase.server.configuration.ServerRole
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * An existing deployment sets no role and must keep behaving exactly as before: a data plane with
 * no `/control` surface at all.
 */
class DefaultRoleE2ETest : E2ETestBase() {
    @Autowired
    private lateinit var properties: ServerProperties

    @Test
    fun `defaults to the data plane`() {
        assertEquals(ServerRole.DATA, properties.role)
    }

    @Test
    fun `does not serve control endpoints`() {
        client
            .get()
            .uri("/control/topology")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
