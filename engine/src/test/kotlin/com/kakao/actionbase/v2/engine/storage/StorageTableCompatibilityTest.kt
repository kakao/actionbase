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
 * Abstract compatibility test for StorageTable implementations.
 *
 * Required operations: get, scan, put, delete, increment, batch, checkAndMutate.
 */
abstract class StorageTableCompatibilityTest {
    protected abstract fun createTable(): StorageTable

    protected open fun supportsCheckAndMutate(): Boolean = true

    protected open fun supportsScanLimit(): Boolean = true

    protected open fun supportsIncrement(): Boolean = true

    private lateinit var table: StorageTable

    @BeforeEach
    fun setUp() {
        table = createTable()
    }

    @Nested
    @DisplayName("get")
    inner class GetTest {
        @Test
        fun `returns value when key exists`() {
            table.put(b("key"), b("value")).block()
            assert(table.get(b("key")).block()?.contentEquals(b("value")) == true)
        }

        @Test
        fun `returns null when key not exists`() {
            assert(table.get(b("missing")).block() == null)
        }

        @Test
        fun `getAll returns matching records`() {
            table.put(b("k1"), b("v1")).block()
            table.put(b("k2"), b("v2")).block()
            assert(table.get(listOf(b("k1"), b("k2"))).block()!!.size == 2)
        }

        @Test
        fun `getAll skips missing keys`() {
            table.put(b("exists"), b("v")).block()
            assert(table.get(listOf(b("exists"), b("missing"))).block()!!.size == 1)
        }
    }

    @Nested
    @DisplayName("scan")
    inner class ScanTest {
        @BeforeEach
        fun setup() {
            listOf("user:001:a", "user:001:b", "user:002:a", "post:001").forEach {
                table.put(b(it), b("v")).block()
            }
        }

        @Test
        fun `returns matching prefix`() {
            val results = table.scan(b("user:001"), 100, null, null).block()!!
            assert(results.size == 2)
            assert(results.all { String(it.key).startsWith("user:001") })
        }

        @Test
        fun `returns empty for non-matching prefix`() {
            assert(table.scan(b("nonexistent"), 100, null, null).block()!!.isEmpty())
        }

        @Test
        fun `returns sorted keys`() {
            val keys = table.scan(b("user:"), 100, null, null).block()!!.map { String(it.key) }
            assert(keys == keys.sorted())
        }

        @Test
        fun `respects limit`() {
            assumeTrue(supportsScanLimit())
            assert(table.scan(b("user:"), 2, null, null).block()!!.size == 2)
        }
    }

    @Nested
    @DisplayName("put")
    inner class PutTest {
        @Test
        fun `stores value`() {
            table.put(b("k"), b("v")).block()
            assert(table.get(b("k")).block()?.contentEquals(b("v")) == true)
        }

        @Test
        fun `overwrites existing`() {
            table.put(b("k"), b("old")).block()
            table.put(b("k"), b("new")).block()
            assert(String(table.get(b("k")).block()!!) == "new")
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTest {
        @Test
        fun `removes key`() {
            table.put(b("k"), b("v")).block()
            table.delete(b("k")).block()
            assert(table.get(b("k")).block() == null)
        }

        @Test
        fun `silently succeeds for missing key`() {
            table.delete(b("nonexistent")).block()
        }
    }

    @Nested
    @DisplayName("increment")
    inner class IncrementTest {
        @BeforeEach
        fun checkSupport() {
            assumeTrue(supportsIncrement())
        }

        @Test
        fun `creates counter if not exists`() {
            assert(table.increment(b("cnt"), 10).block() == 10L)
        }

        @Test
        fun `updates existing counter`() {
            table.put(b("cnt"), longToBytes(100)).block()
            assert(table.increment(b("cnt"), 50).block() == 150L)
        }

        @Test
        fun `decrements with negative delta`() {
            table.put(b("cnt"), longToBytes(100)).block()
            assert(table.increment(b("cnt"), -30).block() == 70L)
        }
    }

    @Nested
    @DisplayName("batch")
    inner class BatchTest {
        @Test
        fun `executes puts`() {
            table.batch(listOf(MutationRequest.Put(b("b1"), b("v1")), MutationRequest.Put(b("b2"), b("v2")))).block()
            assert(table.get(listOf(b("b1"), b("b2"))).block()!!.size == 2)
        }

        @Test
        fun `executes deletes`() {
            table.put(b("d1"), b("v")).block()
            table.put(b("d2"), b("v")).block()
            table.batch(listOf(MutationRequest.Delete(b("d1")), MutationRequest.Delete(b("d2")))).block()
            assert(table.get(listOf(b("d1"), b("d2"))).block()!!.isEmpty())
        }

        @Test
        fun `executes increments`() {
            assumeTrue(supportsIncrement())
            table.batch(listOf(MutationRequest.Increment(b("c1"), 10), MutationRequest.Increment(b("c2"), 20))).block()
            assert(bytesToLong(table.get(b("c1")).block()!!) == 10L)
            assert(bytesToLong(table.get(b("c2")).block()!!) == 20L)
        }

        @Test
        fun `executes mixed mutations`() {
            assumeTrue(supportsIncrement())
            table.put(b("to-delete"), b("v")).block()
            table
                .batch(
                    listOf(
                        MutationRequest.Put(b("new"), b("v")),
                        MutationRequest.Delete(b("to-delete")),
                        MutationRequest.Increment(b("cnt"), 100),
                    ),
                ).block()
            assert(table.get(b("new")).block() != null)
            assert(table.get(b("to-delete")).block() == null)
            assert(bytesToLong(table.get(b("cnt")).block()!!) == 100L)
        }
    }

    @Nested
    @DisplayName("exists")
    inner class ExistsTest {
        @Test
        fun `returns true when key exists`() {
            table.put(b("k"), b("v")).block()
            assert(table.exists(b("k")).block() == true)
        }

        @Test
        fun `returns false when key not exists`() {
            assert(table.exists(b("missing")).block() == false)
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
                assert(table.setIfNotExists(b("lock"), b("owner")).block() == true)
                assert(table.get(b("lock")).block()?.contentEquals(b("owner")) == true)
            }

            @Test
            fun `fails when key exists`() {
                table.put(b("lock"), b("existing")).block()
                assert(table.setIfNotExists(b("lock"), b("new")).block() == false)
                assert(String(table.get(b("lock")).block()!!) == "existing")
            }
        }

        @Nested
        @DisplayName("deleteIfEquals")
        inner class DeleteIfEqualsTest {
            @Test
            fun `succeeds when value matches`() {
                table.put(b("lock"), b("owner")).block()
                assert(table.deleteIfEquals(b("lock"), b("owner")).block() == true)
                assert(table.get(b("lock")).block() == null)
            }

            @Test
            fun `fails when value differs`() {
                table.put(b("lock"), b("owner")).block()
                assert(table.deleteIfEquals(b("lock"), b("different")).block() == false)
                assert(table.get(b("lock")).block() != null)
            }

            @Test
            fun `fails when key not exists`() {
                assert(table.deleteIfEquals(b("missing"), b("v")).block() == false)
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
                            if (table.setIfNotExists(b("lock"), b("owner-$i")).block() == true) {
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
                table.put(b("lock"), b("owner-0")).block()
                val threads = 10
                val released = AtomicInteger(0)
                val latch = CountDownLatch(threads)
                val executor = Executors.newFixedThreadPool(threads)

                repeat(threads) { i ->
                    executor.submit {
                        try {
                            if (table.deleteIfEquals(b("lock"), b("owner-$i")).block() == true) {
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
