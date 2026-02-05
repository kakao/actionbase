package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.core.storage.MutationRequest

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Abstract compatibility test for StorageBucket implementations.
 *
 * Required operations: get, scan, put, delete, increment, batch, checkAndMutate.
 */
abstract class StorageBucketCompatibilityTest {
    protected abstract fun createBucket(): StorageBucket

    protected open fun supportsCheckAndMutate(): Boolean = true

    protected open fun supportsScanLimit(): Boolean = true

    private lateinit var bucket: StorageBucket

    @BeforeEach
    fun setUp() {
        bucket = createBucket()
    }

    @Nested
    @DisplayName("get")
    inner class GetTest {
        @Test
        fun `returns value when key exists`() {
            bucket.put(b("key"), b("value")).block()
            assert(bucket.get(b("key")).block()?.contentEquals(b("value")) == true)
        }

        @Test
        fun `returns null when key not exists`() {
            assert(bucket.get(b("missing")).block() == null)
        }

        @Test
        fun `getAll returns matching records`() {
            bucket.put(b("k1"), b("v1")).block()
            bucket.put(b("k2"), b("v2")).block()
            assert(bucket.get(listOf(b("k1"), b("k2"))).block()!!.size == 2)
        }

        @Test
        fun `getAll skips missing keys`() {
            bucket.put(b("exists"), b("v")).block()
            assert(bucket.get(listOf(b("exists"), b("missing"))).block()!!.size == 1)
        }
    }

    @Nested
    @DisplayName("scan")
    inner class ScanTest {
        @BeforeEach
        fun setup() {
            listOf("user:001:a", "user:001:b", "user:002:a", "post:001").forEach {
                bucket.put(b(it), b("v")).block()
            }
        }

        @Test
        fun `returns matching prefix`() {
            val results = bucket.scan(b("user:001"), 100, null, null).block()!!
            assert(results.size == 2)
            assert(results.all { String(it.key).startsWith("user:001") })
        }

        @Test
        fun `returns empty for non-matching prefix`() {
            assert(bucket.scan(b("nonexistent"), 100, null, null).block()!!.isEmpty())
        }

        @Test
        fun `returns sorted keys`() {
            val keys = bucket.scan(b("user:"), 100, null, null).block()!!.map { String(it.key) }
            assert(keys == keys.sorted())
        }

        @Test
        fun `respects limit`() {
            assumeTrue(supportsScanLimit())
            assert(bucket.scan(b("user:"), 2, null, null).block()!!.size == 2)
        }
    }

    @Nested
    @DisplayName("put")
    inner class PutTest {
        @Test
        fun `stores value`() {
            bucket.put(b("k"), b("v")).block()
            assert(bucket.get(b("k")).block()?.contentEquals(b("v")) == true)
        }

        @Test
        fun `overwrites existing`() {
            bucket.put(b("k"), b("old")).block()
            bucket.put(b("k"), b("new")).block()
            assert(String(bucket.get(b("k")).block()!!) == "new")
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTest {
        @Test
        fun `removes key`() {
            bucket.put(b("k"), b("v")).block()
            bucket.delete(b("k")).block()
            assert(bucket.get(b("k")).block() == null)
        }

        @Test
        fun `silently succeeds for missing key`() {
            bucket.delete(b("nonexistent")).block()
        }
    }

    @Nested
    @DisplayName("increment")
    inner class IncrementTest {
        @Test
        fun `creates counter if not exists`() {
            assert(bucket.increment(b("cnt"), 10).block() == 10L)
        }

        @Test
        fun `updates existing counter`() {
            bucket.put(b("cnt"), longToBytes(100)).block()
            assert(bucket.increment(b("cnt"), 50).block() == 150L)
        }

        @Test
        fun `decrements with negative delta`() {
            bucket.put(b("cnt"), longToBytes(100)).block()
            assert(bucket.increment(b("cnt"), -30).block() == 70L)
        }
    }

    @Nested
    @DisplayName("batch")
    inner class BatchTest {
        @Test
        fun `executes puts`() {
            bucket.batch(listOf(MutationRequest.Put(b("b1"), b("v1")), MutationRequest.Put(b("b2"), b("v2")))).block()
            assert(bucket.get(listOf(b("b1"), b("b2"))).block()!!.size == 2)
        }

        @Test
        fun `executes deletes`() {
            bucket.put(b("d1"), b("v")).block()
            bucket.put(b("d2"), b("v")).block()
            bucket.batch(listOf(MutationRequest.Delete(b("d1")), MutationRequest.Delete(b("d2")))).block()
            assert(bucket.get(listOf(b("d1"), b("d2"))).block()!!.isEmpty())
        }

        @Test
        fun `executes increments`() {
            bucket.batch(listOf(MutationRequest.Increment(b("c1"), 10), MutationRequest.Increment(b("c2"), 20))).block()
            assert(bytesToLong(bucket.get(b("c1")).block()!!) == 10L)
            assert(bytesToLong(bucket.get(b("c2")).block()!!) == 20L)
        }

        @Test
        fun `executes mixed mutations`() {
            bucket.put(b("to-delete"), b("v")).block()
            bucket
                .batch(
                    listOf(
                        MutationRequest.Put(b("new"), b("v")),
                        MutationRequest.Delete(b("to-delete")),
                        MutationRequest.Increment(b("cnt"), 100),
                    ),
                ).block()
            assert(bucket.get(b("new")).block() != null)
            assert(bucket.get(b("to-delete")).block() == null)
            assert(bytesToLong(bucket.get(b("cnt")).block()!!) == 100L)
        }
    }

    @Nested
    @DisplayName("exists")
    inner class ExistsTest {
        @Test
        fun `returns true when key exists`() {
            bucket.put(b("k"), b("v")).block()
            assert(bucket.exists(b("k")).block() == true)
        }

        @Test
        fun `returns false when key not exists`() {
            assert(bucket.exists(b("missing")).block() == false)
        }
    }

    @Nested
    @DisplayName("checkAndMutate")
    inner class CheckAndMutateTest {
        @BeforeEach
        fun checkSupport() {
            assumeTrue(supportsCheckAndMutate())
        }

        @Nested
        @DisplayName("setIfNotExists")
        inner class SetIfNotExistsTest {
            @Test
            fun `succeeds when key not exists`() {
                assert(bucket.setIfNotExists(b("lock"), b("owner")).block() == true)
                assert(bucket.get(b("lock")).block()?.contentEquals(b("owner")) == true)
            }

            @Test
            fun `fails when key exists`() {
                bucket.put(b("lock"), b("existing")).block()
                assert(bucket.setIfNotExists(b("lock"), b("new")).block() == false)
                assert(String(bucket.get(b("lock")).block()!!) == "existing")
            }
        }

        @Nested
        @DisplayName("deleteIfEquals")
        inner class DeleteIfEqualsTest {
            @Test
            fun `succeeds when value matches`() {
                bucket.put(b("lock"), b("owner")).block()
                assert(bucket.deleteIfEquals(b("lock"), b("owner")).block() == true)
                assert(bucket.get(b("lock")).block() == null)
            }

            @Test
            fun `fails when value differs`() {
                bucket.put(b("lock"), b("owner")).block()
                assert(bucket.deleteIfEquals(b("lock"), b("different")).block() == false)
                assert(bucket.get(b("lock")).block() != null)
            }

            @Test
            fun `fails when key not exists`() {
                assert(bucket.deleteIfEquals(b("missing"), b("v")).block() == false)
            }
        }

        @Nested
        @DisplayName("concurrent")
        inner class ConcurrentTest {
            @Test
            fun `only one thread acquires lock`() {
                val threads = 10
                val acquired = AtomicInteger(0)
                val latch = CountDownLatch(threads)
                val executor = Executors.newFixedThreadPool(threads)

                repeat(threads) { i ->
                    executor.submit {
                        try {
                            if (bucket.setIfNotExists(b("lock"), b("owner-$i")).block() == true) {
                                acquired.incrementAndGet()
                            }
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await()
                executor.shutdown()
                assert(acquired.get() == 1) { "Expected 1 but got ${acquired.get()}" }
            }

            @Test
            fun `only owner releases lock`() {
                bucket.put(b("lock"), b("owner-0")).block()
                val threads = 10
                val released = AtomicInteger(0)
                val latch = CountDownLatch(threads)
                val executor = Executors.newFixedThreadPool(threads)

                repeat(threads) { i ->
                    executor.submit {
                        try {
                            if (bucket.deleteIfEquals(b("lock"), b("owner-$i")).block() == true) {
                                released.incrementAndGet()
                            }
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await()
                executor.shutdown()
                assert(released.get() == 1) { "Expected 1 but got ${released.get()}" }
            }
        }
    }

    companion object {
        fun b(s: String): ByteArray = s.toByteArray()

        fun longToBytes(v: Long): ByteArray =
            ByteBuffer
                .allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(v)
                .array()

        fun bytesToLong(b: ByteArray): Long = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).long
    }
}
