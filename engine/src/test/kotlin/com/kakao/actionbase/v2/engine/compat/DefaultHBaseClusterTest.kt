package com.kakao.actionbase.v2.engine.compat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultHBaseClusterTest {

    @Test
    fun `missing kerberos realm should throw for secure cluster`() {
        val properties = mapOf(
            "secure" to "true",
            "version" to "2.4",
            "namespace" to "test",
            "hbase.zookeeper.quorum" to "localhost:2181",
            "krb5ConfPath" to "/tmp/krb5.conf",
            "keytabPath" to "/tmp/test.keytab",
            "principal" to "user@EXAMPLE.COM",
            // kerberos.realm is intentionally missing
        )

        val exception = assertThrows<IllegalStateException> {
            DefaultHBaseCluster.initialize(properties)
        }
        assert(exception.message == "Kerberos realm is not set for secure cluster")
    }

    @Test
    fun `embedded version should skip kerberos configuration`() {
        val properties = mapOf("version" to "embedded")

        // Should not throw - embedded mode skips all Kerberos/connection setup
        DefaultHBaseCluster.initialize(properties)
    }

    @Test
    fun `empty properties should use mock cluster`() {
        val properties = emptyMap<String, String>()

        // Should not throw - empty properties triggers mock mode
        DefaultHBaseCluster.initialize(properties)
    }
}
