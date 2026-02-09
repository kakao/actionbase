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
    @DisplayName("getBucket")
    inner class GetBucketTest {
        @Test
        fun `returns StorageBuckets with namespace and name`() {
            val buckets = backend.getBucket("test-ns", "test-table").block()!!

            assert(buckets.edge != null)
            assert(buckets.lock != null)
        }

        @Test
        fun `returns StorageBuckets with uri`() {
            val buckets = backend.getBucket("datastore://test-ns/test-table").block()!!

            assert(buckets.edge != null)
            assert(buckets.lock != null)
        }

        @Test
        fun `buckets share the same underlying store`() {
            val buckets = backend.getBucket("test-ns", "test-table").block()!!
            val key = "test-key".toByteArray()
            val value = "test-value".toByteArray()

            buckets.edge.put(key, value).block()

            // Both edge and lock should see the same data since they share the store
            assert(
                buckets.edge
                    .get(key)
                    .block()
                    ?.contentEquals(value) == true,
            )
            assert(
                buckets.lock
                    .get(key)
                    .block()
                    ?.contentEquals(value) == true,
            )
        }

        @Test
        fun `different buckets are isolated from each other`() {
            val buckets1 = backend.getBucket("ns1", "table1").block()!!
            val buckets2 = backend.getBucket("ns2", "table2").block()!!
            val key = "same-key".toByteArray()
            val value1 = "value-from-bucket1".toByteArray()
            val value2 = "value-from-bucket2".toByteArray()

            buckets1.edge.put(key, value1).block()
            buckets2.edge.put(key, value2).block()

            // Each bucket should have its own value for the same key
            assert(
                buckets1.edge
                    .get(key)
                    .block()
                    ?.contentEquals(value1) == true,
            ) { "bucket1 should have value1" }
            assert(
                buckets2.edge
                    .get(key)
                    .block()
                    ?.contentEquals(value2) == true,
            ) { "bucket2 should have value2" }
        }

        @Test
        fun `same namespace and name returns same store`() {
            val buckets1 = backend.getBucket("ns", "table").block()!!
            val buckets2 = backend.getBucket("ns", "table").block()!!
            val key = "test-key".toByteArray()
            val value = "test-value".toByteArray()

            buckets1.edge.put(key, value).block()

            // Second getBucket with same namespace/name should see the data
            assert(
                buckets2.edge
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
