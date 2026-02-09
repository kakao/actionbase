package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HBaseStorageBackendTest {
    @Nested
    @DisplayName("create")
    inner class CreateTest {
        @Test
        fun `throws when namespace is missing`() {
            val props = mapOf("version" to "2.4", "hbase.zookeeper.quorum" to "localhost:2181")

            assertThrows<IllegalArgumentException> {
                HBaseStorageBackend.create(props)
            }
        }

        @Test
        fun `throws when version is unsupported`() {
            val props = mapOf("namespace" to "test", "version" to "3.0")

            assertThrows<IllegalArgumentException> {
                HBaseStorageBackend.create(props)
            }
        }

        @Test
        fun `throws when zookeeper quorum is missing for 2_4`() {
            val props = mapOf("namespace" to "test", "version" to "2.4")

            assertThrows<IllegalStateException> {
                HBaseStorageBackend.create(props)
            }
        }

        @Test
        fun `throws when bootstrap servers is missing for 2_5`() {
            val props = mapOf("namespace" to "test", "version" to "2.5")

            assertThrows<IllegalStateException> {
                HBaseStorageBackend.create(props)
            }
        }

        @Test
        fun `throws when kerberos config is incomplete`() {
            val props =
                mapOf(
                    "namespace" to "test",
                    "version" to "2.4",
                    "hbase.zookeeper.quorum" to "localhost:2181",
                    "secure" to "true",
                )

            assertThrows<IllegalStateException> {
                HBaseStorageBackend.create(props)
            }
        }
    }

    @Nested
    @DisplayName("parseDatastoreUri")
    inner class ParseDatastoreUriTest {
        // Note: parseDatastoreUri is private, so we test it through getBucket
        // These are covered implicitly by integration tests
    }
}
