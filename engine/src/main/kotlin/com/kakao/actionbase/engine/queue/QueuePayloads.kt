package com.kakao.actionbase.engine.queue

data class EnqueueRequest(
    val messages: List<EnqueueMessage>,
)

/** `key` routes to a partition, `seq` orders it, `value` is opaque; `id` is assigned by the server. */
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

/** One partition's page (seq ascending); `offset` is the cursor to pass back to the next poll. */
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

data class QueueCommitResponse(
    val namespace: String,
    val queue: String,
    val partition: Int,
    val committed: Int,
)
