package com.kakao.actionbase.engine.queue

import com.kakao.actionbase.core.codec.XXHash32Wrapper

/** Routes a key to a partition via `floorMod(xxHash32(key), partitions)` (floorMod stays non-negative). */
object PartitionHasher {
    fun partition(
        key: String,
        partitions: Int,
    ): Int {
        require(partitions > 0) { "partitions must be positive, got $partitions" }
        return Math.floorMod(XXHash32Wrapper.default.stringHash(key), partitions)
    }
}
