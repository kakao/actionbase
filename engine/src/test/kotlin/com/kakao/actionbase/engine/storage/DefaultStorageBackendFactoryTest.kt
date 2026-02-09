package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.test.hbase.HBaseTestingClusterExtension

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for DefaultStorageBackendFactory.
 *
 * Uses HBaseTestingClusterExtension to ensure consistent initialization
 * with the HBase testing backend across all tests.
 */
@ExtendWith(HBaseTestingClusterExtension::class)
class DefaultStorageBackendFactoryTest {
    @Nested
    @DisplayName("initialize")
    inner class InitializeTest {
        @Test
        fun `initialize is idempotent`() {
            // Extension already initialized - second call should not throw
            DefaultStorageBackendFactory.initialize(mapOf("type" to "memory"))
            DefaultStorageBackendFactory.initialize(mapOf("type" to "embedded"))

            assert(DefaultStorageBackendFactory.isInitialized)
        }
    }

    @Nested
    @DisplayName("isInitialized")
    inner class IsInitializedTest {
        @Test
        fun `returns true after initialization`() {
            assert(DefaultStorageBackendFactory.isInitialized)
        }
    }

    @Nested
    @DisplayName("close")
    inner class CloseTest {
        @Test
        fun `close is idempotent`() {
            DefaultStorageBackendFactory.close()
            DefaultStorageBackendFactory.close() // Should not throw
        }
    }
}
