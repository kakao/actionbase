package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.engine.storage.memory.MemoryStorageBackend
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertNotNull

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MemoryStorageBackendTest {
    private lateinit var backend: MemoryStorageBackend

    @BeforeEach
    fun setUp() {
        backend = MemoryStorageBackend()
    }

    @AfterEach
    fun tearDown() {
        backend.close()
    }

    @Nested
    @DisplayName("getStorageTable")
    inner class GetStorageTableTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - namespace: test_ns
              name: test_table
            - namespace: ns1
              name: table1
            - namespace: ""
              name: edges
            """,
        )
        fun `returns StorageTable`(
            namespace: String,
            name: String,
        ) {
            val table = backend.getStorageTable(namespace, name).block()!!
            assertNotNull(table)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - uri: datastore://test_ns/test_table
            - uri: datastore://ns1/table1
            """,
        )
        fun `returns StorageTable with uri`(uri: String) {
            val table = backend.getStorageTable(uri).block()!!
            assertNotNull(table)
        }

        @Test
        fun `different tables are isolated from each other`() {
            val table1 = backend.getStorageTable("ns1", "table1").block()!!
            val table2 = backend.getStorageTable("ns2", "table2").block()!!
            val key = "same_key".toByteArray()

            table1.put(key, "v1".toByteArray()).block()
            table2.put(key, "v2".toByteArray()).block()

            assert(String(table1.get(key).block()!!) == "v1") { "table1 should have v1" }
            assert(String(table2.get(key).block()!!) == "v2") { "table2 should have v2" }
        }

        @Test
        fun `same namespace and name returns same store`() {
            val table1 = backend.getStorageTable("ns", "table").block()!!
            val table2 = backend.getStorageTable("ns", "table").block()!!

            table1.put("key".toByteArray(), "value".toByteArray()).block()

            assert(String(table2.get("key".toByteArray()).block()!!) == "value") {
                "same namespace+name should share store"
            }
        }
    }

    @Nested
    @DisplayName("close")
    inner class CloseTest {
        @Test
        fun `close is idempotent`() {
            backend.close()
            backend.close() // Should not throw
        }
    }
}
