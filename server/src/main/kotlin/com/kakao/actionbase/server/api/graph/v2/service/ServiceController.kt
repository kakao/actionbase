package com.kakao.actionbase.server.api.graph.v2.service

import com.kakao.actionbase.server.util.NameValidator
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.ServiceEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class ServiceController(
    val graph: Graph,
) {
    @GetMapping("/graph/v2/service/{service}")
    fun get(
        @PathVariable service: String,
    ): Mono<ResponseEntity<ServiceEntity>> {
        val name = EntityName.fromOrigin(service)
        return graph.serviceDdl.getSingle(name).mapToResponseEntity()
    }

    @GetMapping("/graph/v2/service")
    fun getAll(): Mono<ResponseEntity<DdlPage<ServiceEntity>>> = graph.serviceDdl.getAll(EntityName.origin).mapToResponseEntity()

    @PostMapping("/graph/v2/service/{service}")
    fun create(
        @PathVariable service: String,
        @RequestBody request: ServiceCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<ServiceEntity>>> {
        val name = EntityName.fromOrigin(NameValidator.validate(service, "service"))
        return graph.serviceDdl.create(name, request).mapToResponseEntity()
    }

    @PutMapping("/graph/v2/service/{service}")
    fun update(
        @PathVariable service: String,
        @RequestBody request: ServiceUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<ServiceEntity>>> {
        val name = EntityName.fromOrigin(service)
        return graph.serviceDdl.update(name, request).mapToResponseEntity()
    }
}
