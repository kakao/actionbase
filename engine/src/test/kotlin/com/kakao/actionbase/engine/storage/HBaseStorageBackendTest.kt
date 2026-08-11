package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.engine.storage.hbase.HBaseStorageBackend
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HBaseStorageBackendTest {
    @Nested
    @DisplayName("create")
    inner class CreateTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # namespace missing
            - version: "2.4"
              quorum: localhost:2181
              error: IllegalArgumentException

            # namespace blank — an omitted-namespace ref would resolve to nothing
            - namespace: ""
              version: "2.4"
              quorum: localhost:2181
              error: IllegalArgumentException

            # unsupported version
            - namespace: test
              version: "3.0"
              error: IllegalArgumentException

            # zookeeper quorum missing for 2.4
            - namespace: test
              version: "2.4"
              error: IllegalStateException

            # bootstrap servers missing for 2.5
            - namespace: test
              version: "2.5"
              error: IllegalStateException

            # kerberos config incomplete
            - namespace: test
              version: "2.4"
              quorum: localhost:2181
              secure: "true"
              error: IllegalStateException
            """,
        )
        fun `invalid properties`(
            namespace: String?,
            version: String?,
            quorum: String?,
            secure: String?,
            error: String,
        ) {
            val props =
                buildMap {
                    namespace?.let { put("namespace", it) }
                    version?.let { put("version", it) }
                    quorum?.let { put("hbase.zookeeper.quorum", it) }
                    secure?.let { put("secure", it) }
                }

            when (error) {
                "IllegalArgumentException" ->
                    assertThrows<IllegalArgumentException> {
                        HBaseStorageBackend.create(props)
                    }
                "IllegalStateException" ->
                    assertThrows<IllegalStateException> {
                        HBaseStorageBackend.create(props)
                    }
            }
        }

        @Test
        fun `the configured namespace becomes the default for omitted-namespace refs`() {
            val backend =
                HBaseStorageBackend.create(
                    mapOf(
                        "namespace" to "ab_test",
                        "version" to "2.4",
                        "hbase.zookeeper.quorum" to "localhost:2181",
                    ),
                )

            assertEquals("ab_test", backend.defaultNamespace)
        }
    }
}
