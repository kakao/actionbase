package com.kakao.actionbase.v2.engine.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultHBaseClusterTest {

    private val secureBaseProperties = mapOf(
        "secure" to "true",
        "version" to "2.4",
        "namespace" to "test",
        "hbase.zookeeper.quorum" to "localhost:2181",
        "krb5ConfPath" to "/tmp/krb5.conf",
        "keytabPath" to "/tmp/test.keytab",
        "principal" to "user@EXAMPLE.COM",
    )

    @Test
    fun `missing kerberos realm should throw for secure cluster`() {
        val exception = assertThrows<IllegalStateException> {
            DefaultHBaseCluster.initialize(secureBaseProperties)
        }
        assertEquals("Kerberos realm is not set for secure cluster", exception.message)
    }

    @Test
    fun `blank kerberos realm should throw for secure cluster`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "  ")

        val exception = assertThrows<IllegalArgumentException> {
            DefaultHBaseCluster.initialize(properties)
        }
        assertEquals("Kerberos realm must not be blank", exception.message)
    }

    @Test
    fun `embedded version should skip kerberos configuration`() {
        val properties = mapOf("version" to "embedded")

        DefaultHBaseCluster.initialize(properties)
        assertTrue(DefaultHBaseCluster.INSTANCE.mock)
    }

    @Test
    fun `empty properties should use mock cluster`() {
        val properties = emptyMap<String, String>()

        DefaultHBaseCluster.initialize(properties)
        assertTrue(DefaultHBaseCluster.INSTANCE.mock)
    }
}
