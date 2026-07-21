package com.kakao.actionbase.engine.queue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Queue metadata carried inside the backing immutable edge table's comment, behind a `queue/v1 `
 * marker. A non-queue table's comment simply fails to match.
 */
data class QueueMeta(
    val partitions: Int,
)

object QueueDescriptorCodec {
    const val MARKER = "queue/v1"

    private val mapper = jacksonObjectMapper()

    fun encode(meta: QueueMeta): String = "$MARKER ${mapper.writeValueAsString(meta)}"

    /** Returns the decoded metadata, or null when the comment is not a queue/v1 marker. */
    fun decode(comment: String?): QueueMeta? {
        val prefix = "$MARKER "
        if (comment == null || !comment.startsWith(prefix)) return null
        return runCatching { mapper.readValue<QueueMeta>(comment.removePrefix(prefix).trim()) }.getOrNull()
    }
}
