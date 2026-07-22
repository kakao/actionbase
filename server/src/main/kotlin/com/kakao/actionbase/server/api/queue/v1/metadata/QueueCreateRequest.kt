package com.kakao.actionbase.server.api.queue.v1.metadata

import com.kakao.actionbase.core.Constants

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** Create a queue; `seq`, `value`, and the ULID `id` are fixed system fields, not declared here. */
data class QueueCreateRequest(
    @field:NotBlank(message = "queue is required")
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val queue: String,
    @field:NotBlank(message = "storage is required")
    @field:Pattern(regexp = Constants.Name.STORAGE_URI_PATTERN, message = Constants.Name.STORAGE_URI_MESSAGE)
    val storage: String,
    @field:Min(value = 1, message = "partitions must be at least 1")
    val partitions: Int = DEFAULT_PARTITIONS,
) {
    companion object {
        // 30 = 2·3·5: divisors give many balanced consumer-shard splits.
        const val DEFAULT_PARTITIONS = 30
    }
}
