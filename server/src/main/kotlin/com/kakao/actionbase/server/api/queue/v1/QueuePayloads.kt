package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.types.PrimitiveType

/**
 * Create a queue: a partitioned, append-only log backed by an immutable edge table. `source` is the
 * partition and `target` the message id. `orderBy` names one of the declared [properties] — it must
 * be a non-nullable LONG — which is indexed for per-partition order; poll resumes on it via a range
 * predicate. It is a reference to a caller-declared field, not an injected one.
 */
data class QueueCreateRequest(
    val queue: String,
    val storage: String,
    val partitionCount: Int = DEFAULT_PARTITION_COUNT,
    val orderBy: String = DEFAULT_ORDER_BY,
    val properties: List<QueueField> = emptyList(),
    val comment: String = "",
) {
    companion object {
        // 480 = 2^5 · 3 · 5 — many small divisors keep shard=k/N assignments balanced.
        const val DEFAULT_PARTITION_COUNT = 480
        const val DEFAULT_ORDER_BY = "seq"
    }
}

data class QueueField(
    val name: String,
    val type: PrimitiveType,
    val nullable: Boolean = true,
    val comment: String = "",
)

data class QueueDescriptorResponse(
    val database: String,
    val queue: String,
    val partitionCount: Int,
    val orderBy: String,
    val storage: String,
)

/**
 * Enqueue a batch of messages. `key` is the routing key hashed to a partition, `id` the message id
 * (stored as `target`), and `payload` the declared schema fields — including the queue's `orderBy`
 * field, which supplies the per-partition order value.
 */
data class EnqueueRequest(
    val messages: List<EnqueueMessage>,
)

data class EnqueueMessage(
    val key: String,
    val id: String,
    val payload: Map<String, Any?> = emptyMap(),
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
 * A poll page. `cursor` carries the forward-only per-partition offsets to resume the next poll;
 * it is null once every owned partition is drained. `limit` is applied per partition, so ordering
 * is guaranteed within a partition and best-effort across partitions within a page.
 */
data class PollResponse(
    val messages: List<PolledMessage>,
    val cursor: String?,
    val hasNext: Boolean,
)

data class PolledMessage(
    val partition: Int,
    val id: String,
    val orderBy: Long,
    val payload: Map<String, Any?>,
)
