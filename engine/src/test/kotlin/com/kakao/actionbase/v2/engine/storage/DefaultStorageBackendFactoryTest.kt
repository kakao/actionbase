package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend
import com.kakao.actionbase.v2.engine.storage.mock.MockStorageBackend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultStorageBackendFactoryTest {
    @AfterEach
    fun teardown() {
        DefaultStorageBackendFactory.close()
    }

    @Test
    fun `initialize memory backend`() {
        DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))
        assertTrue(DefaultStorageBackendFactory.INSTANCE is MemoryStorageBackend)
    }

    @Test
    fun `initialize embedded backend`() {
        DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))
        assertTrue(DefaultStorageBackendFactory.INSTANCE is MockStorageBackend)
    }
}
