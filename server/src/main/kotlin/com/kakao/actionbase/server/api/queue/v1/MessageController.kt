package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.engine.queue.EnqueueRequest
import com.kakao.actionbase.engine.queue.EnqueueResponse
import com.kakao.actionbase.engine.queue.PollResponse
import com.kakao.actionbase.engine.queue.QueuePartitionsResponse
import com.kakao.actionbase.engine.queue.QueueService
import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/** Queue runtime HTTP surface: enqueue messages and poll one partition. Delegates to the engine. */
@RestController
class MessageController(
    private val queueService: QueueService,
) {
    @PostMapping("/queue/v1/namespaces/{namespace}/queues/{queue}/messages")
    fun enqueue(
        @PathVariable namespace: String,
        @PathVariable queue: String,
        @RequestBody request: EnqueueRequest,
    ): Mono<ResponseEntity<EnqueueResponse>> = queueService.enqueue(namespace, queue, request).mapToResponseEntity()

    @GetMapping("/queue/v1/namespaces/{namespace}/queues/{queue}/partitions/{partition}/poll")
    fun poll(
        @PathVariable namespace: String,
        @PathVariable queue: String,
        @PathVariable partition: Int,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
        @RequestParam(required = false) offset: Long? = null,
        @RequestParam(required = false) until: Long? = null,
    ): Mono<ResponseEntity<PollResponse>> = queueService.poll(namespace, queue, partition, limit, offset, until).mapToResponseEntity()

    @GetMapping("/queue/v1/namespaces/{namespace}/queues/{queue}/partitions")
    fun partitions(
        @PathVariable namespace: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<QueuePartitionsResponse>> =
        queueService
            .partitions(namespace, queue)
            .map { QueuePartitionsResponse(namespace, queue, it) }
            .mapToResponseEntity()
}
