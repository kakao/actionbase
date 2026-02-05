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
