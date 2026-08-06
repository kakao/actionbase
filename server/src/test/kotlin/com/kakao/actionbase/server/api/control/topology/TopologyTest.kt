package com.kakao.actionbase.server.api.control.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyTest {
    private fun props(vararg tenants: Pair<String, TopologyProperties.Tenant>) = TopologyProperties(tenants.toMap())

    private fun paired(
        env: Env = Env.PROD,
        standby: String? = "http://ab-kc.example.net:8080",
    ) = TopologyProperties.Tenant(
        env = env,
        namespace = "ab_kc",
        activeUrl = "http://ab-kc.example.net",
        standbyUrl = standby,
    )

    @Test
    fun `a tenant resolves to the url of each configured side`() {
        val topology = props("kc" to paired()).toTopology()

        assertEquals("http://ab-kc.example.net", topology.baseUrl("kc", Side.ACTIVE))
        assertEquals("http://ab-kc.example.net:8080", topology.baseUrl("kc", Side.STANDBY))
    }

    @Test
    fun `a trailing slash is trimmed so paths can be appended`() {
        val topology = props("kc" to paired().copy(activeUrl = "http://ab-kc.example.net/")).toTopology()

        assertEquals("http://ab-kc.example.net", topology.baseUrl("kc", Side.ACTIVE))
    }

    @Test
    fun `a relative url is rejected at startup rather than at the first request`() {
        assertThrows(IllegalArgumentException::class.java) {
            props("kc" to paired().copy(activeUrl = "ab-kc.example.net")).toTopology()
        }
    }

    @Test
    fun `an unknown tenant names what is configured`() {
        val topology = props("kc" to paired()).toTopology()

        val thrown = assertThrows(UnknownTenantException::class.java) { topology.tenant("nope") }
        assertTrue(thrown.message!!.contains("[kc]"), thrown.message)
    }

    @Test
    fun `a tenant without a standby has no standby url`() {
        val topology = props("talk" to paired(standby = null)).toTopology()

        assertFalse(topology.tenant("talk").hasStandby)
        assertThrows(UnknownSideException::class.java) { topology.baseUrl("talk", Side.STANDBY) }
    }

    @Test
    fun `fanout reaches both sides when the tenant is paired`() {
        val topology = props("kc" to paired()).toTopology()

        assertEquals(listOf(Side.ACTIVE, Side.STANDBY), topology.sidesFor("kc", fanout = true))
    }

    @Test
    fun `fanout on an unpaired tenant is ignored, not an error`() {
        // A caller should not have to know which tenants are paired to ask for a fanout.
        val topology = props("talk" to paired(standby = null)).toTopology()

        assertEquals(listOf(Side.ACTIVE), topology.sidesFor("talk", fanout = true))
    }

    @Test
    fun `without fanout only the active side is reached`() {
        val topology = props("kc" to paired()).toTopology()

        assertEquals(listOf(Side.ACTIVE), topology.sidesFor("kc", fanout = false))
    }

    @Test
    fun `tenants are listed in a stable order`() {
        val topology =
            props(
                "talk" to paired(standby = null),
                "kc" to paired(),
                "stg" to paired(env = Env.TEST),
            ).toTopology()

        assertEquals(listOf("kc", "stg", "talk"), topology.tenants.map { it.tenant })
    }
}
