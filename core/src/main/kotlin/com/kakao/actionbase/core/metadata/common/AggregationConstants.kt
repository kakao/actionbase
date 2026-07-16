package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.codec.XXHash32Wrapper

object AggregationConstants {
    const val REFRESH_TABLE_DATABASE = "topk"
    const val REFRESH_TABLE_NAME = "refresh"

    const val GLOBAL_ENTITY = "__global__"

    // 2 x 3 x 5 x 7 x 11
    const val REFRESH_PARTITION_COUNT = 2310

    fun scoreSourceKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String? = null,
    ): String = appendSegment("$database.$table:$topk:${direction.name}:$entity", segment)

    // refreshAt is embedded in the key so two events for the same coordinates never collide
    // even when their refresh times differ.
    fun refreshTargetKey(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String? = null,
        target: String,
        refreshAt: Long,
    ): String = "${appendSegment("$database.$table:$topk:${direction.name}:$entity", segment)}:$target:$refreshAt"

    fun refreshPartition(
        database: String,
        table: String,
        topk: String,
        direction: Direction,
        entity: String,
        segment: String? = null,
        target: String,
    ): Long =
        XXHash32Wrapper.default
            .stringHash("${appendSegment("$database.$table:$topk:${direction.name}:$entity", segment)}:$target")
            .mod(REFRESH_PARTITION_COUNT)
            .toLong()

    fun refreshPartitionsFor(
        workerCount: Int,
        workerNumber: Int,
    ): List<Long> {
        require(workerCount > 0) { "workerCount must be greater than 0." }
        require(workerNumber in 1..workerCount) { "workerNumber must be between 1 and workerCount." }

        val workerIndex = workerNumber - 1
        return (0 until REFRESH_PARTITION_COUNT)
            .filter { partition -> partition % workerCount == workerIndex }
            .map { it.toLong() }
    }

    fun refreshWorkerNumberFor(
        partition: Long,
        workerCount: Int,
    ): Int {
        require(workerCount > 0) { "workerCount must be greater than 0." }
        return Math.floorMod(partition, workerCount.toLong()).toInt() + 1
    }

    private fun appendSegment(
        key: String,
        segment: String?,
    ): String = segment?.takeIf { it.isNotBlank() }?.let { "$key:$it" } ?: key
}
