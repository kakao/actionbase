package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import reactor.test.StepVerifier

class MemoryStorageBucketTest {
    private val store = ByteArrayStore()
    private val bucket = MemoryStorageBucket(store)

    @Test
    fun `put and get values`() {
        val key = bytes("key")
        val value = bytes("value")

        StepVerifier
            .create(bucket.put(key, value).then(bucket.get(key)))
            .assertNext { actual -> assertArrayEquals(value, actual) }
            .verifyComplete()
    }

    @Test
    fun `batch applies mutations`() {
        val key = bytes("value-key")
        val counterKey = bytes("count")
        val value = bytes("v1")

        StepVerifier
            .create(
                bucket
                    .batch(
                        listOf(
                            MutationRequest.Put(key, value),
                            MutationRequest.Increment(counterKey, 2L),
                        ),
                    ).then(bucket.increment(counterKey, 3L)),
            ).assertNext { result -> assertEquals(5L, result) }
            .verifyComplete()
    }

    @Test
    fun `exists and delete work`() {
        val key = bytes("exists")
        val value = bytes("yes")

        StepVerifier
            .create(bucket.put(key, value).then(bucket.exists(key)))
            .assertNext { exists -> assertTrue(exists) }
            .verifyComplete()

        StepVerifier
            .create(bucket.delete(key).then(bucket.exists(key)))
            .assertNext { exists -> assertFalse(exists) }
            .verifyComplete()
    }

    @Test
    fun `set if not exists and delete if equals`() {
        val key = bytes("lock")
        val value = bytes("v1")

        StepVerifier
            .create(bucket.setIfNotExists(key, value))
            .assertNext { set -> assertTrue(set) }
            .verifyComplete()

        StepVerifier
            .create(bucket.setIfNotExists(key, value))
            .assertNext { set -> assertFalse(set) }
            .verifyComplete()

        StepVerifier
            .create(bucket.deleteIfEquals(key, value))
            .assertNext { deleted -> assertTrue(deleted) }
            .verifyComplete()

        StepVerifier
            .create(bucket.exists(key))
            .assertNext { exists -> assertFalse(exists) }
            .verifyComplete()
    }

    @Test
    fun `scan returns prefixed results`() {
        val key1 = bytes("p1")
        val key2 = bytes("p2")
        val key3 = bytes("x1")

        StepVerifier
            .create(
                bucket
                    .put(key1, bytes("v1"))
                    .then(bucket.put(key2, bytes("v2")))
                    .then(bucket.put(key3, bytes("v3")))
                    .then(bucket.scan(bytes("p"), 10, null, null)),
            ).assertNext { results ->
                assertEquals(2, results.size)
                assertArrayEquals(key1, results[0].key)
                assertArrayEquals(key2, results[1].key)
            }.verifyComplete()
    }

    private fun bytes(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
}
