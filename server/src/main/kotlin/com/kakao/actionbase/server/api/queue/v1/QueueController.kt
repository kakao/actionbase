package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueueController(
    private val queueService: QueueService,
) {
    @PostMapping("/queue/v1/databases/{database}/queues/{queue}/messages")
    fun enqueue(
        @PathVariable database: String,
        @PathVariable queue: String,
        @RequestBody request: EnqueueRequest,
    ): Mono<ResponseEntity<EnqueueResponse>> = queueService.enqueue(database, queue, request).mapToResponseEntity()
}
