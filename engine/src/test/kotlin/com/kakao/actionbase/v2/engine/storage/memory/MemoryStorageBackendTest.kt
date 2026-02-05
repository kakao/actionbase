package com.kakao.actionbase.v2.engine.storage.memory

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import reactor.test.StepVerifier

class MemoryStorageBackendTest {
    @Test
    fun `getBucket returns shared buckets`() {
        val backend = MemoryStorageBackend()

        StepVerifier
            .create(backend.getBucket("namespace", "name"))
            .assertNext { buckets ->
                assertSame(buckets.edge, buckets.lock)
            }.verifyComplete()
    }
}
