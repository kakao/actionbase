package com.kakao.actionbase.server.control.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyTest {
    private fun props(vararg tenants: Pair<String, TopologyProperties.Tenant>) = TopologyProperties(tenants.toMap())

    private fun paired(
        env: Env = Env.PROD,
        standby: String? = "http://ab-alpha.example.net:8080",
    ) = TopologyProperties.Tenant(
        env = env,
        namespace = "ab_alpha",
        activeUrl = "http://ab-alpha.example.net",
        standbyUrl = standby,
    )

    @Test
    fun `a tenant resolves to the url of each configured side`() {
        val topology = props("alpha" to paired()).toTopology()

        assertEquals("http://ab-alpha.example.net", topology.baseUrl("alpha", Side.ACTIVE))
        assertEquals("http://ab-alpha.example.net:8080", topology.baseUrl("alpha", Side.STANDBY))
    }

    @Test
    fun `a trailing slash is trimmed so paths can be appended`() {
        val topology = props("alpha" to paired().copy(activeUrl = "http://ab-alpha.example.net/")).toTopology()

        assertEquals("http://ab-alpha.example.net", topology.baseUrl("alpha", Side.ACTIVE))
    }

    @Test
    fun `a relative url is rejected at startup rather than at the first request`() {
        assertThrows(IllegalArgumentException::class.java) {
            props("alpha" to paired().copy(activeUrl = "ab-alpha.example.net")).toTopology()
        }
    }

    @Test
    fun `a control instance with no tenants fails at startup`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) { TopologyProperties().toTopology() }
        assertTrue(thrown.message!!.contains("actionbase.control.tenants"), thrown.message)
    }

    @Test
    fun `an unknown tenant names what is configured`() {
        val topology = props("alpha" to paired()).toTopology()

        val thrown = assertThrows(UnknownTenantException::class.java) { topology.tenant("nope") }
        assertTrue(thrown.message!!.contains("[alpha]"), thrown.message)
    }

    @Test
    fun `a tenant without a standby has no standby url`() {
        val topology = props("beta" to paired(standby = null)).toTopology()

        assertFalse(topology.tenant("beta").hasStandby)
        assertThrows(UnknownSideException::class.java) { topology.baseUrl("beta", Side.STANDBY) }
    }

    @Test
    fun `fanout reaches both sides when the tenant is paired`() {
        val topology = props("alpha" to paired()).toTopology()

        assertEquals(listOf(Side.ACTIVE, Side.STANDBY), topology.sidesFor("alpha", fanout = true))
    }

    @Test
    fun `fanout on an unpaired tenant is ignored, not an error`() {
        // A caller should not have to know which tenants are paired to ask for a fanout.
        val topology = props("beta" to paired(standby = null)).toTopology()

        assertEquals(listOf(Side.ACTIVE), topology.sidesFor("beta", fanout = true))
    }

    @Test
    fun `without fanout only the active side is reached`() {
        val topology = props("alpha" to paired()).toTopology()

        assertEquals(listOf(Side.ACTIVE), topology.sidesFor("alpha", fanout = false))
    }

    // Sharing is noticed by two tenants resolving to the same cluster, so this is how it is noticed.
    @Test
    fun `tenants configured against the same cluster get the same cluster key`() {
        val topology =
            props(
                "alpha" to paired(standby = null),
                "beta" to paired(standby = null),
            ).toTopology()

        assertEquals(topology.cluster("alpha", Side.ACTIVE), topology.cluster("beta", Side.ACTIVE))
    }

    @Test
    fun `the cluster key keeps the port so two clusters on one host stay apart`() {
        val topology = props("alpha" to paired()).toTopology()

        assertEquals("ab-alpha.example.net", topology.cluster("alpha", Side.ACTIVE))
        assertEquals("ab-alpha.example.net:8080", topology.cluster("alpha", Side.STANDBY))
    }

    @Test
    fun `env accepts the spelling config uses`() {
        assertEquals(Env.PROD, Env.of("prod"))
        assertEquals(Env.PROD, Env.of("PROD"))
    }

    @Test
    fun `an unknown env names the ones that exist`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) { Env.of("staging") }

        assertTrue(thrown.message!!.contains("dev"), thrown.message)
    }

    @Test
    fun `tenants are listed in a stable order`() {
        val topology =
            props(
                "beta" to paired(standby = null),
                "alpha" to paired(),
                "stg" to paired(env = Env.TEST),
            ).toTopology()

        assertEquals(listOf("alpha", "beta", "stg"), topology.tenants.map { it.tenant })
    }
}
