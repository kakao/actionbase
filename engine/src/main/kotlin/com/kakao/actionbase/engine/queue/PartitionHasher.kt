package com.kakao.actionbase.engine.queue

import com.kakao.actionbase.core.codec.XXHash32Wrapper

/**
 * Maps a routing key to a partition in `[0, partitions)` via `floorMod(xxHash32(key), partitions)`.
 * `floorMod` (not `%`) keeps the result non-negative for hashes that overflow into negatives.
 */
object PartitionHasher {
    fun partition(
        key: String,
        partitions: Int,
    ): Int {
        require(partitions > 0) { "partitions must be positive, got $partitions" }
        return Math.floorMod(XXHash32Wrapper.default.stringHash(key), partitions)
    }
}
