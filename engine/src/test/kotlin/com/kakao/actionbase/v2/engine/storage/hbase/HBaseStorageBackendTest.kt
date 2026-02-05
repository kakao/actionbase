package com.kakao.actionbase.v2.engine.storage.hbase

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import reactor.test.StepVerifier

class HBaseStorageBackendTest {
    @Test
    fun `embedded backend returns buckets`() {
        val backend = HBaseStorageBackend.create(mapOf("version" to "embedded"))

        StepVerifier
            .create(backend.getBucket("datastore://ns/table"))
            .assertNext { buckets ->
                assertSame(buckets.edge, buckets.lock)
                assertTrue(buckets.edge is HBaseStorageBucket)
            }.verifyComplete()

        backend.close()
    }
}
