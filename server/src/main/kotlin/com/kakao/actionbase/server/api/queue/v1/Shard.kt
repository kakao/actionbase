package com.kakao.actionbase.server.api.queue.v1

/**
 * A poll shard `index/count`: consumer `index` of `count` owns every partition `p` where
 * `p % count == index`. Sharding lets independent consumers split a queue's partitions
 * without coordination.
 */
data class Shard(
    val index: Int,
    val count: Int,
) {
    init {
        require(count > 0) { "shard count must be positive, got $count" }
        require(index in 0 until count) { "shard index must be in [0, $count), got $index" }
    }

    /** Partitions owned by this shard within `[0, partitionCount)`, ascending. */
    fun partitionsFor(partitionCount: Int): List<Int> {
        require(partitionCount > 0) { "partitionCount must be positive, got $partitionCount" }
        return (index until partitionCount step count).toList()
    }

    companion object {
        fun parse(value: String): Shard {
            val parts = value.split("/")
            require(parts.size == 2) { "shard must be formatted as `index/count`, got `$value`" }
            val index = parts[0].toIntOrNull() ?: throw IllegalArgumentException("shard index must be an integer, got `${parts[0]}`")
            val count = parts[1].toIntOrNull() ?: throw IllegalArgumentException("shard count must be an integer, got `${parts[1]}`")
            return Shard(index, count)
        }
    }
}
