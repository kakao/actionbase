package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.types.PrimitiveType

/**
 * Create a queue: a partitioned, append-only log backed by an immutable edge table. `source` is
 * the partition, `target` the message id, and `orderBy` a LONG property indexed for per-partition
 * order. `properties` are the message payload fields.
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
