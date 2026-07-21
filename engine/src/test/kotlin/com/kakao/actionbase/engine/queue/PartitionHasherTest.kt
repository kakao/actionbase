package com.kakao.actionbase.engine.queue

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class PartitionHasherTest {
    @Test
    fun `partition is always within bounds`() {
        val partitions = 30
        repeat(10_000) { i ->
            val p = PartitionHasher.partition("key-$i", partitions)
            assertTrue(p in 0 until partitions, "partition $p out of bounds for key-$i")
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
    fun `partitions must be positive`() {
        assertFailsWith<IllegalArgumentException> { PartitionHasher.partition("k", 0) }
        assertFailsWith<IllegalArgumentException> { PartitionHasher.partition("k", -1) }
    }
}
