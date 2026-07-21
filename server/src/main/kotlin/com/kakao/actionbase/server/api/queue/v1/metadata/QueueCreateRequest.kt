package com.kakao.actionbase.server.api.queue.v1.metadata

/** Create a queue; `seq`, `value`, and the ULID `id` are fixed system fields, not declared here. */
data class QueueCreateRequest(
    val queue: String,
    val storage: String,
    val partitions: Int = DEFAULT_PARTITIONS,
) {
    companion object {
        // 30 = 2·3·5: divisors give many balanced consumer-shard splits.
        const val DEFAULT_PARTITIONS = 30
    }
}
