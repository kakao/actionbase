package com.kakao.actionbase.v2.engine.compat

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultStorageBackendTest {
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
