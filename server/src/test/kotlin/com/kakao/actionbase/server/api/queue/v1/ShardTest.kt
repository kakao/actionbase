package com.kakao.actionbase.server.api.queue.v1

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

import org.junit.jupiter.api.Test

class ShardTest {
    @Test
    fun `partitionsFor splits partitions round-robin by shard count`() {
        assertEquals(listOf(0, 3, 6, 9), Shard(0, 3).partitionsFor(10))
        assertEquals(listOf(1, 4, 7), Shard(1, 3).partitionsFor(10))
        assertEquals(listOf(2, 5, 8), Shard(2, 3).partitionsFor(10))
    }

    @Test
    fun `every partition is owned by exactly one shard`() {
        val count = 4
        val owned = (0 until count).flatMap { Shard(it, count).partitionsFor(480) }
        assertEquals((0 until 480).toList(), owned.sorted())
        assertEquals(480, owned.toSet().size)
    }

    @Test
    fun `parse reads index and count from k slash N`() {
        assertEquals(Shard(2, 8), Shard.parse("2/8"))
    }

    @Test
    fun `parse rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> { Shard.parse("2") }
        assertFailsWith<IllegalArgumentException> { Shard.parse("a/8") }
        assertFailsWith<IllegalArgumentException> { Shard.parse("2/b") }
    }

    @Test
    fun `index must be within count`() {
        assertFailsWith<IllegalArgumentException> { Shard(3, 3) }
        assertFailsWith<IllegalArgumentException> { Shard(-1, 3) }
        assertFailsWith<IllegalArgumentException> { Shard(0, 0) }
    }
}
