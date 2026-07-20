package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueueAdminController(
    private val adminService: QueueAdminService,
) {
    @PostMapping("/queue/v1/databases/{database}/queues")
    fun createQueue(
        @PathVariable database: String,
        @RequestBody request: QueueCreateRequest,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> = adminService.createQueue(database, request).mapToResponseEntity()

    @GetMapping("/queue/v1/databases/{database}/queues/{queue}")
    fun getQueue(
        @PathVariable database: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> = adminService.getQueue(database, queue).mapToResponseEntity()

    @DeleteMapping("/queue/v1/databases/{database}/queues/{queue}")
    fun deleteQueue(
        @PathVariable database: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<Void>> = adminService.deleteQueue(database, queue).thenReturn(ResponseEntity.noContent().build())
}
