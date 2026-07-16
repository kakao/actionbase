package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.v2.core.code.EdgeBuffer
import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Pins the v3 and v2 default codec buffer size and covers write-boundary behavior for each. */
class DefaultBufferCapacityTest {
    companion object {
        private const val BUFFER_SIZE = 8192
    }

    @Test
    fun `v3 and v2 default codec buffer sizes match the default size`() {
        assertEquals(BUFFER_SIZE, Constants.Codec.DEFAULT_BUFFER_SIZE)
        assertEquals(BUFFER_SIZE, EdgeBuffer.DEFAULT_CAPACITY)
    }

    @Test
    fun `v3 buffer pool accepts writes up to the default capacity`() {
        ByteArrayBufferPool.default.use { buffer ->
            ByteArray(BUFFER_SIZE).also { buffer.put(it) }
        }
    }

    @Test
    fun `v3 buffer pool throws when writing beyond the default capacity`() {
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            ByteArrayBufferPool.default.use { buffer ->
                ByteArray(BUFFER_SIZE + 1).also { buffer.put(it) }
            }
        }
    }

    @Test
    fun `v2 edge encoder accepts writes up to the default capacity`() {
        val encoder = EdgeEncoderFactory().bytesKeyValueEncoder
        encoder.useAsByteArray { buffer ->
            buffer.put(ByteArray(BUFFER_SIZE), 0, BUFFER_SIZE)
        }
    }

    @Test
    fun `v2 edge encoder throws when writing beyond the default capacity`() {
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            val encoder = EdgeEncoderFactory().bytesKeyValueEncoder
            encoder.useAsByteArray { buffer ->
                buffer.put(ByteArray(BUFFER_SIZE + 1), 0, BUFFER_SIZE + 1)
            }
        }
    }
}
