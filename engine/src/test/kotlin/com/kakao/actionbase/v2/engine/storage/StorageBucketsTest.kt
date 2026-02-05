package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import reactor.core.publisher.Mono

class StorageBucketsTest {
    @Test
    fun `keeps edge and lock buckets`() {
        val edgeBucket = StubBucket()
        val lockBucket = StubBucket()

        val buckets = StorageBuckets(edge = edgeBucket, lock = lockBucket)

        assertSame(edgeBucket, buckets.edge)
        assertSame(lockBucket, buckets.lock)
    }

    private class StubBucket : StorageBucket {
        override fun get(key: ByteArray): Mono<ByteArray?> = Mono.just(null)

        override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> = Mono.just(emptyList())

        override fun put(
            key: ByteArray,
            value: ByteArray,
        ): Mono<Void> = Mono.empty()

        override fun delete(key: ByteArray): Mono<Void> = Mono.empty()

        override fun scan(
            prefix: ByteArray,
            limit: Int,
            start: ByteArray?,
            stop: ByteArray?,
        ): Mono<List<HBaseRecord>> = Mono.just(emptyList())

        override fun increment(
            key: ByteArray,
            delta: Long,
        ): Mono<Long> = Mono.just(0L)

        override fun batch(requests: List<MutationRequest>): Mono<Void> = Mono.empty()

        override fun exists(key: ByteArray): Mono<Boolean> = Mono.just(false)

        override fun setIfNotExists(
            key: ByteArray,
            value: ByteArray,
        ): Mono<Boolean> = Mono.just(false)

        override fun deleteIfEquals(
            key: ByteArray,
            expectedValue: ByteArray,
        ): Mono<Boolean> = Mono.just(false)
    }
}
