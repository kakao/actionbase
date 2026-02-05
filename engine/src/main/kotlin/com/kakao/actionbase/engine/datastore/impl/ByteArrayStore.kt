package com.kakao.actionbase.engine.datastore.impl

import com.kakao.actionbase.core.storage.HBaseRecord

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Thread-safe key-value store using ByteArray as keys in lexicographical order.
 *
 * Features:
 * - Basic put/get operations with defensive copying
 * - Prefix-based range scanning
 * - Atomic check-and-set (CAS) operations
 * - Atomic increment operations for 8-byte Long values
 *
 * Thread-safety is guaranteed by ConcurrentSkipListMap's atomic operations.
 * All keys and values are defensively copied to prevent external mutation.
 */
class ByteArrayStore internal constructor() {
    private val underlying: NavigableMap<ByteArray, ByteArray> = ConcurrentSkipListMap(ByteArrayComparator)

    // ========== Read Operations ==========

    val size: Int get() = underlying.size

    fun isEmpty(): Boolean = underlying.isEmpty()

    /** Returns a copy of the value associated with the key. */
    operator fun get(key: ByteArray): ByteArray? = underlying[key]?.clone()

    /**
     * Scans all entries with keys starting with the given prefix.
     * Returns copies of keys and values.
     */
    fun prefixScan(prefix: ByteArray): List<HBaseRecord> =
        prefixScanAsMap(prefix.clone())
            .entries
            .map { HBaseRecord(key = it.key.clone(), value = it.value.clone()) }
            .toList()

    internal fun prefixScanAsMap(prefix: ByteArray): NavigableMap<ByteArray, ByteArray> {
        val toKey = createUpperBoundKey(prefix)

        return if (toKey != null) {
            underlying.subMap(prefix, true, toKey, false)
        } else {
            // All bytes are 0xFF - scan from prefix to end
            underlying.tailMap(prefix, true)
        }
    }

    // ========== Write Operations ==========

    /**
     * Stores a key-value pair. Both key and value are defensively copied.
     * Returns a copy of the previous value, if any.
     */
    operator fun set(
        key: ByteArray,
        value: ByteArray,
    ): ByteArray? = underlying.put(key.clone(), value.clone())?.clone()

    /** Removes the key. Returns a copy of the removed value, if any. */
    fun remove(key: ByteArray): ByteArray? = underlying.remove(key)?.clone()

    /**
     * Atomically increments the value by delta.
     * Value must be an 8-byte Long in Big-Endian format.
     * Starts from 0 if the key doesn't exist.
     *
     * @throws IllegalArgumentException if the existing value is not 8 bytes
     */
    fun increment(
        key: ByteArray,
        delta: Long,
    ): Long {
        val result =
            underlying.compute(key.clone()) { _, currentValue ->
                val current =
                    currentValue?.let {
                        require(it.size == 8) { "Value must be 8 bytes for increment, but was ${it.size}" }
                        ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).long
                    } ?: 0L
                val next = current + delta
                ByteBuffer
                    .allocate(8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(next)
                    .array()
            }
        return ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN).long
    }

    /**
     * Atomically updates the value only if the current value matches the expectedValue.
     * Uses content comparison (not reference equality).
     *
     * @param expectedValue expected current value (null means key should not exist)
     * @param newValue new value to set (null means delete the key)
     * @return true if the update succeeded
     */
    fun checkAndSet(
        key: ByteArray,
        expectedValue: ByteArray?,
        newValue: ByteArray?,
    ): Boolean {
        var success = false
        underlying.compute(key.clone()) { _, currentValue ->
            // Content-based comparison (null-safe)
            success =
                when {
                    currentValue == null && expectedValue == null -> true
                    currentValue != null &&
                        expectedValue != null &&
                        currentValue.contentEquals(expectedValue) -> true
                    else -> false
                }

            if (success) {
                newValue?.clone() // null deletes the key
            } else {
                currentValue // keep existing value on failure
            }
        }
        return success
    }

    companion object {
        /**
         * Creates the exclusive upper-bound key for prefix scanning.
         * Returns null if all bytes are 0xFF (no upper bound exists).
         */
        private fun createUpperBoundKey(prefix: ByteArray): ByteArray? {
            val upperBound = prefix.copyOf()

            // Find the rightmost byte that can be incremented
            for (i in upperBound.indices.reversed()) {
                val unsigned = upperBound[i].toInt() and 0xFF
                if (unsigned < 0xFF) {
                    upperBound[i] = (unsigned + 1).toByte()
                    return upperBound
                }
                upperBound[i] = 0
            }

            return null // All bytes are 0xFF
        }

        /** Compares ByteArrays in unsigned lexicographical order. */
        private object ByteArrayComparator : Comparator<ByteArray> {
            override fun compare(
                a: ByteArray,
                b: ByteArray,
            ): Int {
                val minLen = minOf(a.size, b.size)

                // Compare common bytes as unsigned
                for (i in 0 until minLen) {
                    val byteA = a[i].toInt() and 0xFF
                    val byteB = b[i].toInt() and 0xFF
                    if (byteA != byteB) {
                        return byteA.compareTo(byteB)
                    }
                }

                // Shorter array comes first
                return a.size.compareTo(b.size)
            }
        }
    }
}
