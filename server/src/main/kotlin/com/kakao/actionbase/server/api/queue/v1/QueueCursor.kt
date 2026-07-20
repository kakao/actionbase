package com.kakao.actionbase.server.api.queue.v1

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Forward-only poll cursor: per-partition scan offsets accumulated across polls. Serialized as
 * a versioned binary blob, base64url-encoded so it survives as a query-string token. Partitions
 * with no further pages are simply absent — the cursor only ever carries partitions still in flight.
 */
data class QueueCursor(
    val offsets: Map<Int, String>,
) {
    fun offsetFor(partition: Int): String? = offsets[partition]

    fun encode(): String {
        val bytes =
            ByteArrayOutputStream()
                .also { baos ->
                    DataOutputStream(baos).use { out ->
                        out.writeByte(VERSION)
                        out.writeInt(offsets.size)
                        offsets.toSortedMap().forEach { (partition, offset) ->
                            out.writeInt(partition)
                            out.writeUTF(offset)
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
                val offsets = LinkedHashMap<Int, String>(size)
                repeat(size) {
                    val partition = input.readInt()
                    offsets[partition] = input.readUTF()
                }
                return QueueCursor(offsets)
            }
        }
    }
}
