package com.kakao.actionbase.engine.queue

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class QueueMeta(
    val numPartitions: Int,
)

/** Queue config stored in the backing table's comment behind a `queue/v1 ` marker. */
object QueueDescriptorCodec {
    const val MARKER = "queue/v1"

    private val mapper = jacksonObjectMapper()

    fun encode(meta: QueueMeta): String = "$MARKER ${mapper.writeValueAsString(meta)}"

    fun decode(comment: String?): QueueMeta? {
        val prefix = "$MARKER "
        if (comment == null || !comment.startsWith(prefix)) return null
        return runCatching { mapper.readValue<QueueMeta>(comment.removePrefix(prefix).trim()) }.getOrNull()
    }
}
