package com.kakao.actionbase.core.codec

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

import org.junit.jupiter.api.Test

class ByteArrayBufferPoolTest {
    companion object {
        private const val BUFFER_SIZE = 64
    }

    @Test
    fun `use should encode with a new buffer when the pool is exhausted`() {
        val pool = ByteArrayBufferPool.create(poolSize = 1, bufferSize = BUFFER_SIZE)

        val outer =
            pool.use { pooled ->
                pooled.putValue(1)
                val inner =
                    pool.use { allocated ->
                        assertNotSame(pooled, allocated)
                        allocated.putValue(2).toByteArray()
                    }
                assertContentEquals(unpooled(2), inner)
                pooled.toByteArray()
            }

        assertContentEquals(unpooled(1), outer)
    }

    @Test
    fun `acquire should allocate a buffer of the pool's buffer size when the pool is exhausted`() {
        val pool = ByteArrayBufferPool.create(poolSize = 1, bufferSize = BUFFER_SIZE)
        val pooled = pool.acquire()

        val allocated = pool.acquire()

        assertNotSame(pooled, allocated)
        assertEquals(BUFFER_SIZE, allocated.length)
    }

    private fun unpooled(value: Int): ByteArray = ByteArray(BUFFER_SIZE).buffer().putValue(value).toByteArray()
}
