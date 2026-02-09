package com.kakao.actionbase.v2.engine.compat

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend
import org.junit.jupiter.api.Assertions.assertEquals
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
        val kerberosRealm = HBaseStorageBackend.resolveKerberosRealm(secureBaseProperties, null)

        assertEquals(HBaseStorageBackend.LEGACY_DEFAULT_KERBEROS_REALM, kerberosRealm)
    }

    @Test
    fun `environment kerberos realm should be used when property is missing`() {
        val kerberosRealm = HBaseStorageBackend.resolveKerberosRealm(secureBaseProperties, "ENV.EXAMPLE.COM")

        assertEquals("ENV.EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `property kerberos realm should override environment realm`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "PROP.EXAMPLE.COM")

        val kerberosRealm = HBaseStorageBackend.resolveKerberosRealm(properties, "ENV.EXAMPLE.COM")

        assertEquals("PROP.EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `kerberos realm should be trimmed`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "  EXAMPLE.COM  ")

        val kerberosRealm = HBaseStorageBackend.resolveKerberosRealm(properties, null)

        assertEquals("EXAMPLE.COM", kerberosRealm)
    }

    @Test
    fun `blank kerberos realm should throw`() {
        val properties = secureBaseProperties + ("kerberos.realm" to "  ")

        val exception =
            assertThrows<IllegalArgumentException> {
                HBaseStorageBackend.resolveKerberosRealm(properties, null)
            }
        assertEquals("Kerberos realm must not be blank", exception.message)
    }
}
