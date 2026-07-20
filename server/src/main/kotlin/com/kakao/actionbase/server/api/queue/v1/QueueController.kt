package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
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

    @GetMapping("/queue/v1/databases/{database}/queues/{queue}/poll")
    fun poll(
        @PathVariable database: String,
        @PathVariable queue: String,
        @RequestParam(required = false, defaultValue = "0/1") shard: String,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false) cursor: String? = null,
    ): Mono<ResponseEntity<PollResponse>> = queueService.poll(database, queue, Shard.parse(shard), limit, cursor).mapToResponseEntity()
}
