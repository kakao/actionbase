package com.kakao.actionbase.v2.engine.storage.inmemory

import com.kakao.actionbase.v2.engine.storage.Delete
import com.kakao.actionbase.v2.engine.storage.Get
import com.kakao.actionbase.v2.engine.storage.Increment
import com.kakao.actionbase.v2.engine.storage.Put
import com.kakao.actionbase.v2.engine.storage.Scan
import com.kakao.actionbase.v2.engine.storage.result.GetResult

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import reactor.test.StepVerifier

class InMemoryStorageBackendTest {
    private val backend = InMemoryStorageBackend()

    @Test
    fun `should perform put, get, scan, increment, and delete operations`() {
        val bucket = backend.bucket("test_bucket").block()!!
        val key = "test_key".toByteArray()
        val value = "test_value".toByteArray()

        // Put
        StepVerifier
            .create(bucket.put(Put(key, value)))
            .verifyComplete()

        // Get Found
        StepVerifier
            .create(bucket.get(Get(key)))
            .assertNext { result ->
                assertTrue(result is GetResult.Found)
                assertEquals(value.contentToString(), (result as GetResult.Found).value.contentToString())
            }.verifyComplete()

        // Scan
        StepVerifier
            .create(bucket.scan(Scan(key)))
            .assertNext { result ->
                assertEquals(key.contentToString(), result.key.contentToString())
                assertEquals(value.contentToString(), result.value.contentToString())
            }.verifyComplete()

        // Increment
        val counterKey = "counter".toByteArray()
        StepVerifier
            .create(bucket.increment(Increment(counterKey, 5)))
            .assertNext { result ->
                assertEquals(5L, result)
            }.verifyComplete()

        StepVerifier
            .create(bucket.increment(Increment(counterKey, 10)))
            .assertNext { result ->
                assertEquals(15L, result)
            }.verifyComplete()

        // Delete
        StepVerifier
            .create(bucket.delete(Delete(key)))
            .verifyComplete()

        // Get NotFound
        StepVerifier
            .create(bucket.get(Get(key)))
            .assertNext { result ->
                assertTrue(result is GetResult.NotFound)
            }.verifyComplete()
    }

    @Test
    fun `should manage buckets`() {
        backend.bucket("bucket1").block()
        backend.bucket("bucket2").block()

        StepVerifier
            .create(backend.buckets().flatMap { it.names() })
            .assertNext { names ->
                assertTrue(names.containsAll(setOf("bucket1", "bucket2")))
            }.verifyComplete()
    }
}
