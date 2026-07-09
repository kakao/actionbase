package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.v2.core.code.EdgeBuffer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Pins the v3 and v2 default codec buffer size and covers write-boundary behavior for each. */
class DefaultBufferCapacityTest {
    companion object {
        private const val BUFFER_SIZE = 8192
    }

    @Test
    fun `v3 and v2 default codec buffer sizes are default size`() {
        assertEquals(BUFFER_SIZE, Constants.Codec.DEFAULT_BUFFER_SIZE)
        assertEquals(BUFFER_SIZE, EdgeBuffer.DEFAULT_CAPACITY)
    }

    @Test
    fun `v3 buffer accepts writes up to the default capacity`() {
        val pool = ByteArrayBufferPool.create(poolSize = 1, bufferSize = BUFFER_SIZE)
        val buffer = pool.acquire()

        buffer.put(ByteArray(BUFFER_SIZE))
    }

    @Test
    fun `v3 buffer throws when writing beyond the default capacity`() {
        val pool = ByteArrayBufferPool.create(poolSize = 1, bufferSize = BUFFER_SIZE)
        val buffer = pool.acquire()

        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            buffer.put(ByteArray(BUFFER_SIZE + 1))
        }
    }

    @Test
    fun `v2 buffer accepts writes up to the default capacity`() {
        val buffer = EdgeBuffer()

        buffer.put(ByteArray(BUFFER_SIZE), 0, BUFFER_SIZE)
    }

    @Test
    fun `v2 buffer throws when writing beyond the default capacity`() {
        val buffer = EdgeBuffer()

        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            buffer.put(ByteArray(BUFFER_SIZE + 1), 0, BUFFER_SIZE + 1)
        }
    }
}
