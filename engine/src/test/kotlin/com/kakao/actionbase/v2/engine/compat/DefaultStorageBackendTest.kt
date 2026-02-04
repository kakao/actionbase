package com.kakao.actionbase.v2.engine.compat

import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultStorageBackendTest {
    @Test
    fun `use memory backend when type is memory`() {
        DefaultStorageBackend.initialize(
            mapOf(
                "type" to "memory",
                "namespace" to "test_ns",
            ),
        )

        val backend = DefaultStorageBackend.INSTANCE
        assertTrue(backend is MemoryStorageBackend)
        assertEquals("test_ns", backend.namespace)
    }

    @Test
    fun `use hbase backend when type is absent`() {
        DefaultStorageBackend.initialize(
            mapOf(
                "version" to "embedded",
            ),
        )

        val backend = DefaultStorageBackend.INSTANCE
        assertTrue(backend is DefaultHBaseCluster)
        assertTrue(backend.mock)
    }
}
