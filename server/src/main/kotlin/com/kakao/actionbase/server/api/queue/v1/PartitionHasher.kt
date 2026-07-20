package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.codec.XXHash32Wrapper

/**
 * Maps a routing key to a partition in `[0, partitionCount)` via `floorMod(xxHash32(key), partitionCount)`.
 * `floorMod` (not `%`) keeps the result non-negative for hashes that overflow into negatives.
 */
object PartitionHasher {
    fun partition(
        key: String,
        partitionCount: Int,
    ): Int {
        require(partitionCount > 0) { "partitionCount must be positive, got $partitionCount" }
        return Math.floorMod(XXHash32Wrapper.default.stringHash(key), partitionCount)
    }
}
