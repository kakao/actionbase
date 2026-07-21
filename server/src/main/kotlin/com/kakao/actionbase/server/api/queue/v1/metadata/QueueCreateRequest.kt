package com.kakao.actionbase.server.api.queue.v1.metadata

/**
 * Create a queue: a partitioned, append-only log backed by an immutable edge table. The queue
 * declares no schema of its own — the backing table's `seq` (order) and `value` (opaque body) are
 * fixed system fields, and the message `id` is a server-assigned ULID.
 */
data class QueueCreateRequest(
    val queue: String,
    val storage: String,
    val partitions: Int = DEFAULT_PARTITIONS,
) {
    companion object {
        // 30 = 2·3·5 — divisors 1,2,3,5,6,10,15,30 keep partition subsets balanced while keeping
        // single-partition poll cheap.
        const val DEFAULT_PARTITIONS = 30
    }
}
