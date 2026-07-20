package com.kakao.actionbase.server.api.queue.v1

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class PartitionHasherTest {
    @Test
    fun `partition is always within bounds`() {
        val partitionCount = 30
        repeat(10_000) { i ->
            val p = PartitionHasher.partition("key-$i", partitionCount)
            assertTrue(p in 0 until partitionCount, "partition $p out of bounds for key-$i")
        }
    }

    @Test
    fun `partition is deterministic for the same key`() {
        assertEquals(
            PartitionHasher.partition("user-42", 30),
            PartitionHasher.partition("user-42", 30),
        )
    }

    @Test
    fun `partitionCount must be positive`() {
        assertFailsWith<IllegalArgumentException> { PartitionHasher.partition("k", 0) }
        assertFailsWith<IllegalArgumentException> { PartitionHasher.partition("k", -1) }
    }
}
