package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.Constants

import java.util.concurrent.ConcurrentLinkedQueue

import org.slf4j.LoggerFactory

class ByteArrayBufferPool(
    private val pool: ConcurrentLinkedQueue<ByteArrayBuffer>,
    private val bufferSize: Int = Constants.Codec.DEFAULT_BUFFER_SIZE,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ByteArrayBufferPool::class.java)

        @JvmStatic
        fun create(
            poolSize: Int,
            bufferSize: Int,
        ): ByteArrayBufferPool {
            val calculatedPoolSize =
                if (poolSize <= 0) {
                    Constants.Codec.DEFAULT_POOL_SIZE
                } else {
                    poolSize
                }

            logger.info("Creating ByteArrayBufferPool with poolSize: $calculatedPoolSize, bufferSize: $bufferSize")
            val pool = ConcurrentLinkedQueue<ByteArrayBuffer>()

            for (i in 0 until calculatedPoolSize) {
                pool.add(ByteArrayBuffer(bufferSize))
            }

            return ByteArrayBufferPool(pool, bufferSize)
        }

        @JvmStatic
        val default by lazy {
            create(
                poolSize = Constants.Codec.DEFAULT_POOL_SIZE,
                bufferSize = Constants.Codec.DEFAULT_BUFFER_SIZE,
            )
        }
    }

    fun use(accept: (buffer: ByteArrayBuffer) -> ByteArray): ByteArray {
        val buffer = acquire()

        val value: ByteArray

        try {
            buffer.reset()
            value = accept(buffer)
        } finally {
            release(buffer)
        }

        return value
    }

    fun acquire(): ByteArrayBuffer = pool.poll() ?: ByteArrayBuffer(bufferSize)

    fun release(buffer: ByteArrayBuffer) {
        pool.add(buffer)
    }
}
