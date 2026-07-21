package com.kakao.actionbase.server.api.queue.v1.metadata

import com.kakao.actionbase.server.api.graph.v3.metadata.V3NameValidator
import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import jakarta.validation.Valid
import reactor.core.publisher.Mono

/** Queue DDL HTTP surface: create, inspect, enable / disable, and delete queues. */
@RestController
@Validated
class QueueController(
    private val service: QueueMetadataService,
) {
    @PostMapping("/queue/v1/namespaces/{namespace}/queues")
    fun createQueue(
        @PathVariable namespace: String,
        @Valid @RequestBody request: QueueCreateRequest,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> = service.createQueue(V3NameValidator.validate(namespace, "namespace"), request).mapToResponseEntity()

    @GetMapping("/queue/v1/namespaces/{namespace}/queues/{queue}")
    fun getQueue(
        @PathVariable namespace: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> =
        service
            .getQueue(V3NameValidator.validate(namespace, "namespace"), V3NameValidator.validate(queue, "queue"))
            .mapToResponseEntity()

    @PutMapping("/queue/v1/namespaces/{namespace}/queues/{queue}/enable")
    fun enableQueue(
        @PathVariable namespace: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> =
        service
            .setActive(V3NameValidator.validate(namespace, "namespace"), V3NameValidator.validate(queue, "queue"), true)
            .mapToResponseEntity()

    @PutMapping("/queue/v1/namespaces/{namespace}/queues/{queue}/disable")
    fun disableQueue(
        @PathVariable namespace: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<QueueDescriptorResponse>> =
        service
            .setActive(V3NameValidator.validate(namespace, "namespace"), V3NameValidator.validate(queue, "queue"), false)
            .mapToResponseEntity()

    @DeleteMapping("/queue/v1/namespaces/{namespace}/queues/{queue}")
    fun deleteQueue(
        @PathVariable namespace: String,
        @PathVariable queue: String,
    ): Mono<ResponseEntity<Void>> =
        service
            .deleteQueue(V3NameValidator.validate(namespace, "namespace"), V3NameValidator.validate(queue, "queue"))
            .thenReturn(ResponseEntity.noContent().build())
}
