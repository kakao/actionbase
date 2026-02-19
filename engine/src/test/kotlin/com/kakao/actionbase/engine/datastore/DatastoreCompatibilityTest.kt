package com.kakao.actionbase.engine.datastore

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Abstract compatibility test for storage backends.
 *
 * Required operations: get, scan, put, delete, increment, batch, checkAndMutate.
 *
 * @see <a href="/website/src/content/docs/design/storage-backends.mdx">Storage Backends</a>
 */
abstract class DatastoreCompatibilityTest {
    protected abstract fun createStore(): StorageOperations

    protected open fun cleanup() {}

    protected open fun supportsCheckAndMutate(): Boolean = true

    protected open fun supportsScanLimit(): Boolean = true

    private lateinit var store: StorageOperations

    @BeforeEach fun setUp() {
        store = createStore()
    }

    @AfterEach fun tearDown() {
        cleanup()
    }

    @Nested
    @DisplayName("get")
    inner class GetTest {
        @Test fun `returns value when key exists`() {
            store.put(b("key"), b("value"))
            assert(store.get(b("key"))?.contentEquals(b("value")) == true)
        }

        @Test fun `returns null when key not exists`() {
            assert(store.get(b("missing")) == null)
        }

        @Test fun `getAll returns matching records`() {
            store.put(b("k1"), b("v1"))
            store.put(b("k2"), b("v2"))
            assert(store.getAll(listOf(b("k1"), b("k2"))).size == 2)
        }

        @Test fun `getAll skips missing keys`() {
            store.put(b("exists"), b("v"))
            assert(store.getAll(listOf(b("exists"), b("missing"))).size == 1)
        }
    }

    @Nested
    @DisplayName("scan")
    inner class ScanTest {
        @BeforeEach fun setup() {
            listOf("user:001:a", "user:001:b", "user:002:a", "post:001").forEach {
                store.put(b(it), b("v"))
            }
        }

        @Test fun `returns matching prefix`() {
            val results = store.scan(b("user:001"), 100)
            assert(results.size == 2)
            assert(results.all { String(it.first).startsWith("user:001") })
        }

        @Test fun `returns empty for non-matching prefix`() {
            assert(store.scan(b("nonexistent"), 100).isEmpty())
        }

        @Test fun `returns sorted keys`() {
            val keys = store.scan(b("user:"), 100).map { String(it.first) }
            assert(keys == keys.sorted())
        }

        @Test fun `respects limit`() {
            assumeTrue(supportsScanLimit())
            assert(store.scan(b("user:"), 2).size == 2)
        }
    }

    @Nested
    @DisplayName("put")
    inner class PutTest {
        @Test fun `stores value`() {
            store.put(b("k"), b("v"))
            assert(store.get(b("k"))?.contentEquals(b("v")) == true)
        }

        @Test fun `overwrites existing`() {
            store.put(b("k"), b("old"))
            store.put(b("k"), b("new"))
            assert(String(store.get(b("k"))!!) == "new")
        }
    }

    @Nested
    @DisplayName("delete")
    inner class DeleteTest {
        @Test fun `removes key`() {
            store.put(b("k"), b("v"))
            store.delete(b("k"))
            assert(store.get(b("k")) == null)
        }

        @Test fun `silently succeeds for missing key`() {
            store.delete(b("nonexistent"))
        }
    }

    @Nested
    @DisplayName("increment")
    inner class IncrementTest {
        @Test fun `creates counter if not exists`() {
            assert(store.increment(b("cnt"), 10) == 10L)
        }

        @Test fun `updates existing counter`() {
            store.put(b("cnt"), longToBytes(100))
            assert(store.increment(b("cnt"), 50) == 150L)
        }

        @Test fun `decrements with negative delta`() {
            store.put(b("cnt"), longToBytes(100))
            assert(store.increment(b("cnt"), -30) == 70L)
        }
    }

    @Nested
    @DisplayName("batch")
    inner class BatchTest {
        @Test fun `executes puts`() {
            store.batch(listOf(Mutation.Put(b("b1"), b("v1")), Mutation.Put(b("b2"), b("v2"))))
            assert(store.getAll(listOf(b("b1"), b("b2"))).size == 2)
        }

        @Test fun `executes deletes`() {
            store.put(b("d1"), b("v"))
            store.put(b("d2"), b("v"))
            store.batch(listOf(Mutation.Delete(b("d1")), Mutation.Delete(b("d2"))))
            assert(store.getAll(listOf(b("d1"), b("d2"))).isEmpty())
        }

        @Test fun `executes increments`() {
            store.batch(listOf(Mutation.Increment(b("c1"), 10), Mutation.Increment(b("c2"), 20)))
            assert(bytesToLong(store.get(b("c1"))!!) == 10L)
            assert(bytesToLong(store.get(b("c2"))!!) == 20L)
        }

        @Test fun `executes mixed mutations`() {
            store.put(b("to-delete"), b("v"))
            store.batch(
                listOf(
                    Mutation.Put(b("new"), b("v")),
                    Mutation.Delete(b("to-delete")),
                    Mutation.Increment(b("cnt"), 100),
                ),
            )
            assert(store.get(b("new")) != null)
            assert(store.get(b("to-delete")) == null)
            assert(bytesToLong(store.get(b("cnt"))!!) == 100L)
        }
    }

    @Nested
    @DisplayName("checkAndMutate")
    inner class CheckAndMutateTest {
        @BeforeEach fun checkSupport() {
            assumeTrue(supportsCheckAndMutate())
        }

        @Nested
        @DisplayName("setIfNotExists")
        inner class SetIfNotExistsTest {
            @Test fun `succeeds when key not exists`() {
                assert(store.setIfNotExists(b("lock"), b("owner")))
                assert(store.get(b("lock"))?.contentEquals(b("owner")) == true)
            }

            @Test fun `fails when key exists`() {
                store.put(b("lock"), b("existing"))
                assert(!store.setIfNotExists(b("lock"), b("new")))
                assert(String(store.get(b("lock"))!!) == "existing")
            }
        }

        @Nested
        @DisplayName("deleteIfEquals")
        inner class DeleteIfEqualsTest {
            @Test fun `succeeds when value matches`() {
                store.put(b("lock"), b("owner"))
                assert(store.deleteIfEquals(b("lock"), b("owner")))
                assert(store.get(b("lock")) == null)
            }

            @Test fun `fails when value differs`() {
                store.put(b("lock"), b("owner"))
                assert(!store.deleteIfEquals(b("lock"), b("different")))
                assert(store.get(b("lock")) != null)
            }

            @Test fun `fails when key not exists`() {
                assert(!store.deleteIfEquals(b("missing"), b("v")))
            }
        }

        @Nested
        @DisplayName("concurrent")
        inner class ConcurrentTest {
            @Test fun `only one thread acquires lock`() {
                val threads = 10
                val acquired = AtomicInteger(0)
                val latch = CountDownLatch(threads)
                val executor = Executors.newFixedThreadPool(threads)

                repeat(threads) { i ->
                    executor.submit {
                        try {
                            if (store.setIfNotExists(b("lock"), b("owner-$i"))) {
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

            @Test fun `only owner releases lock`() {
                store.put(b("lock"), b("owner-0"))
                val threads = 10
                val released = AtomicInteger(0)
                val latch = CountDownLatch(threads)
                val executor = Executors.newFixedThreadPool(threads)

                repeat(threads) { i ->
                    executor.submit {
                        try {
                            if (store.deleteIfEquals(b("lock"), b("owner-$i"))) {
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
