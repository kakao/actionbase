package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.MockHBaseStorageBackend
import com.kakao.actionbase.v2.engine.storage.memory.MemoryStorageBackend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        fun `creates MockHBaseStorageBackend for type embedded`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))

            assert(DefaultStorageBackendFactory.INSTANCE is MockHBaseStorageBackend)
        }

        @Test
        fun `creates MockHBaseStorageBackend for empty properties`() {
            DefaultStorageBackendFactory.initialize(emptyMap())

            assert(DefaultStorageBackendFactory.INSTANCE is MockHBaseStorageBackend)
        }

        @Test
        fun `creates MockHBaseStorageBackend for version embedded`() {
            DefaultStorageBackendFactory.initialize(mapOf("version" to "embedded"))

            assert(DefaultStorageBackendFactory.INSTANCE is MockHBaseStorageBackend)
        }

        @Test
        fun `throws when already initialized`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))

            assertThrows<IllegalStateException> {
                DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))
            }
        }

        @Test
        fun `allows re-initialization after reset`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))
            assert(DefaultStorageBackendFactory.INSTANCE is MemoryStorageBackend)

            DefaultStorageBackendFactory.reset()
            DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))

            assert(DefaultStorageBackendFactory.INSTANCE is MockHBaseStorageBackend)
        }
    }

    @Nested
    @DisplayName("isInitialized")
    inner class IsInitializedTest {
        @Test
        fun `returns false before initialization`() {
            assert(!DefaultStorageBackendFactory.isInitialized)
        }

        @Test
        fun `returns true after initialization`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))

            assert(DefaultStorageBackendFactory.isInitialized)
        }

        @Test
        fun `returns false after reset`() {
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))
            DefaultStorageBackendFactory.reset()

            assert(!DefaultStorageBackendFactory.isInitialized)
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
