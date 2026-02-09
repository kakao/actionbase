package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend

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
    @DisplayName("open")
    inner class OpenTest {
        @Test
        fun `returns StorageTable with namespace and name`() {
            val table = backend.open("test-ns", "test-table").block()!!

            assert(table != null)
        }

        @Test
        fun `returns StorageTable with uri`() {
            val table = backend.open("datastore://test-ns/test-table").block()!!

            assert(table != null)
        }

        @Test
        fun `different tables are isolated from each other`() {
            val table1 = backend.open("ns1", "table1").block()!!
            val table2 = backend.open("ns2", "table2").block()!!
            val key = "same-key".toByteArray()
            val value1 = "value-from-table1".toByteArray()
            val value2 = "value-from-table2".toByteArray()

            table1.put(key, value1).block()
            table2.put(key, value2).block()

            // Each table should have its own value for the same key
            assert(
                table1
                    .get(key)
                    .block()
                    ?.contentEquals(value1) == true,
            ) { "table1 should have value1" }
            assert(
                table2
                    .get(key)
                    .block()
                    ?.contentEquals(value2) == true,
            ) { "table2 should have value2" }
        }

        @Test
        fun `same namespace and name returns same store`() {
            val table1 = backend.open("ns", "table").block()!!
            val table2 = backend.open("ns", "table").block()!!
            val key = "test-key".toByteArray()
            val value = "test-value".toByteArray()

            table1.put(key, value).block()

            // Second open with same namespace/name should see the data
            assert(
                table2
                    .get(key)
                    .block()
                    ?.contentEquals(value) == true,
            ) { "same namespace+name should share store" }
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
