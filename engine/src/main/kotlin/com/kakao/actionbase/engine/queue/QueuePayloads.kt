package com.kakao.actionbase.engine.queue

/**
 * Enqueue a batch of messages. `key` is the routing key hashed to a partition, `seq` the LONG order
 * value (log sequence or refresh due time), and `value` an opaque body (any JSON). The message `id`
 * is a server-assigned ULID, returned in [EnqueueResult].
 */
data class EnqueueRequest(
    val messages: List<EnqueueMessage>,
)

data class EnqueueMessage(
    val key: String,
    val seq: Long,
    val value: Any? = null,
)

data class EnqueueResponse(
    val accepted: Int,
    val results: List<EnqueueResult>,
)

data class EnqueueResult(
    val partition: Int,
    val id: String,
    val status: String,
)

/**
 * A poll page over one partition, ordered by `seq` ascending. `offset` is the forward cursor (the
 * max `seq` in the page, retained when the partition drains) — the same field is passed back on the
 * next request. `hasNext` reports whether more is immediately available.
 */
data class PollResponse(
    val messages: List<PolledMessage>,
    val offset: Long?,
    val hasNext: Boolean,
)

data class PolledMessage(
    val partition: Int,
    val id: String,
    val seq: Long,
    val value: Any?,
)

/** The queue's partition count, for consumers that fan a poll loop across `0 .. partitions-1`. */
data class QueuePartitionsResponse(
    val namespace: String,
    val queue: String,
    val partitions: Int,
)
