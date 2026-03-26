package com.kakao.actionbase.core.edge.mapper

import com.kakao.actionbase.core.codec.ByteArrayBuffer
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.codec.MapperExtensions.decodeEdgeKeyPrefix
import com.kakao.actionbase.core.codec.MapperExtensions.encodeKeyPrefix
import com.kakao.actionbase.core.codec.OrderedHeader
import com.kakao.actionbase.core.codec.XXHash32Wrapper
import com.kakao.actionbase.core.codec.buffer
import com.kakao.actionbase.core.codec.getValue
import com.kakao.actionbase.core.codec.getValueOrNull
import com.kakao.actionbase.core.codec.hasRemaining
import com.kakao.actionbase.core.codec.putValue
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.storage.HBaseRecord

class EdgeCacheRecordMapper private constructor(
    val encoder: Encoder,
    val decoder: Decoder,
) {
    companion object {
        @JvmStatic
        fun create(
            bufferPool: ByteArrayBufferPool = ByteArrayBufferPool.Companion.default,
            xxHash32Wrapper: XXHash32Wrapper = XXHash32Wrapper.default,
        ): EdgeCacheRecordMapper =
            EdgeCacheRecordMapper(
                encoder = Encoder(bufferPool, xxHash32Wrapper),
                decoder = Decoder(),
            )
    }

    class Encoder(
        private val bufferPool: ByteArrayBufferPool,
        private val xxHash32Wrapper: XXHash32Wrapper,
    ) {
        fun encode(record: EdgeCacheRecord): HBaseRecord {
            val key = encodeKey(record.key)
            val qualifier = encodeQualifier(record.qualifier)
            val value = encodeValue(record.value)
            return HBaseRecord(key = key, qualifier = qualifier, value = value)
        }

        fun encodeKey(key: EdgeCacheRecord.Key): ByteArray =
            bufferPool.use { buffer ->
                buffer
                    .encodeKey(key)
                    .toByteArray()
            }

        private fun ByteArrayBuffer.encodeKey(key: EdgeCacheRecord.Key): ByteArrayBuffer {
            this
                .encodeKeyPrefix(xxHash32Wrapper, key.directedSource, key.tableCode, key.recordTypeCode)
                .putValue(key.direction.code)
                .putValue(key.cacheCode)
            return this
        }

        fun encodeQualifier(qualifier: EdgeCacheRecord.Qualifier): ByteArray =
            bufferPool.use { buffer ->
                buffer
                    .encodeQualifier(qualifier)
                    .toByteArray()
            }

        private fun ByteArrayBuffer.encodeQualifier(qualifier: EdgeCacheRecord.Qualifier): ByteArrayBuffer {
            this
                .also {
                    for (cacheValue in qualifier.cacheValues) {
                        it.putValue(value = cacheValue.value, order = cacheValue.order)
                    }
                }.putValue(qualifier.directedTarget)
            return this
        }

        fun encodeValue(value: EdgeCacheRecord.Value): ByteArray =
            bufferPool.use { buffer: ByteArrayBuffer ->
                buffer
                    .putValue(value.version)
                    .also {
                        value.properties.forEach { keyCode, propertyValue: Any? ->
                            it.putValue(keyCode)
                            it.putValue(propertyValue)
                        }
                    }.toByteArray()
            }
    }

    class Decoder {
        fun decode(
            key: ByteArray,
            qualifier: ByteArray,
            value: ByteArray,
        ): EdgeCacheRecord {
            val edgeKey = decodeKey(key)
            val edgeQualifier = decodeQualifier(qualifier)
            val edgeValue = decodeValue(value)

            return EdgeCacheRecord(
                key = edgeKey,
                qualifier = edgeQualifier,
                value = edgeValue,
            )
        }

        fun decodeKey(key: ByteArray): EdgeCacheRecord.Key {
            val buffer = key.buffer()

            val prefix = buffer.decodeEdgeKeyPrefix()

            val directionCode: Byte = buffer.getValue()
            val cacheCode: Int = buffer.getValue()

            return EdgeCacheRecord.Key.of(
                directedSource = prefix.source,
                tableCode = prefix.tableCode,
                direction = Direction.of(directionCode),
                cacheCode = cacheCode,
            )
        }

        fun decodeQualifier(qualifier: ByteArray): EdgeCacheRecord.Qualifier {
            val buffer = qualifier.buffer()
            val values = mutableListOf<EdgeCacheRecord.Qualifier.CacheValue>()

            while (buffer.hasRemaining()) {
                val orderedHeader = OrderedHeader.of(code = buffer.peek())
                val value: Any? = buffer.getValueOrNull()
                values.add(EdgeCacheRecord.Qualifier.CacheValue(value, order = orderedHeader.order))
            }

            require(values.isNotEmpty()) { "EdgeCacheRecord qualifier must contain at least a target." }

            val directedTarget =
                requireNotNull(values.last().value) {
                    "the target of the edge cache must not be null."
                }

            return EdgeCacheRecord.Qualifier(
                cacheValues = values.dropLast(1),
                directedTarget = directedTarget,
            )
        }

        fun decodeValue(value: ByteArray): EdgeCacheRecord.Value {
            val buffer = value.buffer()
            val version: Long = buffer.getValue()

            val properties = mutableMapOf<Int, Any?>()

            while (buffer.hasRemaining()) {
                val propertyHashKey: Int = buffer.getValue()
                val propertyValue: Any? = buffer.getValueOrNull()

                properties[propertyHashKey] = propertyValue
            }

            return EdgeCacheRecord.Value(version, properties.toMap())
        }
    }
}
