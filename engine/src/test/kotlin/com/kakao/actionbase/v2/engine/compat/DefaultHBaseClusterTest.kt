package com.kakao.actionbase.v2.engine.compat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultHBaseClusterTest {
    private val secureBaseProperties =
        mapOf(
            "secure" to "true",
            "version" to "2.4",
            "namespace" to "test",
            "hbase.zookeeper.quorum" to "localhost:2181",
            "krb5ConfPath" to "/tmp/krb5.conf",
            "keytabPath" to "/tmp/test.keytab",
            "principal" to "user@EXAMPLE.COM",
        )

    @Test
    fun `missing kerberos realm should use legacy default for compatibility`() {
        val kerberosRealm = DefaultHBaseCluster.resolveKerberosRealm(secureBaseProperties, null)

        assertEquals(DefaultHBaseCluster.LEGACY_DEFAULT_KERBEROS_REALM, kerberosRealm)
    }

    @Test
    fun `environment kerberos realm should be used when property is missing`() {
        val kerberosRealm = DefaultHBaseCluster.resolveKerberosRealm(secureBaseProperties, "ENV.EXAMPLE.COM")

        assertEquals("ENV.EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `property kerberos realm should override environment realm`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "PROP.EXAMPLE.COM")

        val kerberosRealm = DefaultHBaseCluster.resolveKerberosRealm(properties, "ENV.EXAMPLE.COM")

        assertEquals("PROP.EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `kerberos realm should be trimmed`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "  EXAMPLE.COM  ")

        val kerberosRealm = DefaultHBaseCluster.resolveKerberosRealm(properties, null)

        assertEquals("EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `blank kerberos realm should throw for secure cluster`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "  ")

        val exception =
            assertThrows<IllegalArgumentException> {
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
