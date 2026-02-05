package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend
import com.kakao.actionbase.v2.engine.storage.mock.MockStorageBackend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DefaultStorageBackendFactoryTest {
    @AfterEach
    fun tearDown() {
        DefaultStorageBackendFactory.reset()
    }

    @Nested
    @DisplayName("initialize")
    inner class InitializeTest {
        @Test
        fun `creates MemoryStorageBackend for type memory`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))

            assert(DefaultStorageBackendFactory.INSTANCE is MemoryStorageBackend)
        }

        @Test
        fun `creates MockStorageBackend for type embedded`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))

            assert(DefaultStorageBackendFactory.INSTANCE is MockStorageBackend)
        }

        @Test
        fun `creates MockStorageBackend for empty properties`() {
            DefaultStorageBackendFactory.initialize(emptyMap())

            assert(DefaultStorageBackendFactory.INSTANCE is MockStorageBackend)
        }

        @Test
        fun `creates MockStorageBackend for version embedded`() {
            DefaultStorageBackendFactory.initialize(mapOf("version" to "embedded"))

            assert(DefaultStorageBackendFactory.INSTANCE is MockStorageBackend)
        }
    }

    @Nested
    @DisplayName("close")
    inner class CloseTest {
        @Test
        fun `close is idempotent before initialization`() {
            DefaultStorageBackendFactory.close() // Should not throw
        }

        @Test
        fun `close is idempotent after initialization`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))
            DefaultStorageBackendFactory.close()
            DefaultStorageBackendFactory.close() // Should not throw
        }
    }
}
