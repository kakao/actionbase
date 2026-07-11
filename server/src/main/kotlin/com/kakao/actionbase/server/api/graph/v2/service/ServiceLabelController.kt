package com.kakao.actionbase.server.api.graph.v2.service

import com.kakao.actionbase.server.util.NameValidator
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCopyRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest
import com.kakao.actionbase.v2.engine.util.getLogger

import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import jakarta.validation.Valid
import reactor.core.publisher.Mono

@RestController
@Validated
class ServiceLabelController(
    val graph: Graph,
) {
    @GetMapping("/graph/v2/service/{service}/label/{label}")
    fun get(
        @PathVariable service: String,
        @PathVariable label: String,
    ): Mono<ResponseEntity<LabelEntity>> {
        val name = EntityName(service, label)
        return graph.labelDdl.getSingle(name).mapToResponseEntity()
    }

    @GetMapping("/graph/v2/service/{service}/label")
    fun getAll(
        @PathVariable service: String,
    ): Mono<ResponseEntity<DdlPage<LabelEntity>>> = graph.labelDdl.getAll(EntityName(service)).mapToResponseEntity()

    @PostMapping("/graph/v2/service/{service}/label/{label}")
    fun create(
        @PathVariable service: String,
        @PathVariable label: String,
        @Valid @RequestBody request: LabelCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<LabelEntity>>> {
        val name = EntityName(NameValidator.validate(service, "service"), NameValidator.validate(label, "label"))
        return graph.labelDdl.create(name, request).mapToResponseEntity()
    }

    @PutMapping("/graph/v2/service/{service}/label/{label}")
    fun update(
        @PathVariable service: String,
        @PathVariable label: String,
        @RequestBody request: LabelUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<LabelEntity>>> {
        getLogger().warn("update request: $request")
        val name = EntityName(service, label)
        return graph.labelDdl.update(name, request).mapToResponseEntity()
    }

    @PostMapping("/graph/v2/service/{service}/label/{label}/copy")
    fun copy(
        @PathVariable service: String,
        @PathVariable label: String,
        @Valid @RequestBody request: LabelCopyRequest,
    ): Mono<ResponseEntity<DdlStatus<LabelEntity>>> {
        val from = EntityName(service, label)
        val to = EntityName(service, NameValidator.validate(request.target, "target"))
        return graph.labelDdl.copy(from, to, mapOf("storage" to request.storage)).mapToResponseEntity()
    }
}
