package com.kakao.actionbase.server.api.queue.v1

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Forward-only poll cursor: the last-consumed `orderBy` value per partition. A poll resumes each
 * partition strictly after its recorded position (`orderBy > position`), so drained partitions are
 * never re-read and simply yield nothing until higher-ordered messages arrive. Serialized as a
 * versioned binary blob, base64url-encoded so it survives as a query-string token. Requires
 * `orderBy` to be unique per partition (a monotonic sequence), which a queue log guarantees.
 */
data class QueueCursor(
    val positions: Map<Int, Long>,
) {
    fun positionOf(partition: Int): Long? = positions[partition]

    fun encode(): String {
        val bytes =
            ByteArrayOutputStream()
                .also { baos ->
                    DataOutputStream(baos).use { out ->
                        out.writeByte(VERSION)
                        out.writeInt(positions.size)
                        positions.toSortedMap().forEach { (partition, position) ->
                            out.writeInt(partition)
                            out.writeLong(position)
                        }
                    }
                }.toByteArray()
        return ENCODER.encodeToString(bytes)
    }

    companion object {
        const val VERSION = 1
        val EMPTY = QueueCursor(emptyMap())

        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        fun decode(encoded: String?): QueueCursor {
            if (encoded.isNullOrBlank()) return EMPTY
            val bytes = DECODER.decode(encoded)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readByte().toInt()
                require(version == VERSION) { "unsupported queue cursor version: $version" }
                val size = input.readInt()
                val positions = LinkedHashMap<Int, Long>(size)
                repeat(size) {
                    val partition = input.readInt()
                    positions[partition] = input.readLong()
                }
                return QueueCursor(positions)
            }
        }
    }
}
