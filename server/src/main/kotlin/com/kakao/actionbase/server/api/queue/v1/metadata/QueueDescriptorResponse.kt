package com.kakao.actionbase.server.api.queue.v1.metadata

data class QueueDescriptorResponse(
    val namespace: String,
    val queue: String,
    val partitions: Int,
    val storage: String,
)
