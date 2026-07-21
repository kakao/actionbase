package com.kakao.actionbase.engine.queue

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object PartitionHasher {
    fun partition(
        key: String,
        numPartitions: Int,
    ): Int {
        require(numPartitions > 0) { "numPartitions must be positive, got $numPartitions" }
        return Math.floorMod(XXHash32Wrapper.default.stringHash(key), numPartitions)
    }
}
