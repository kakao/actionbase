package com.kakao.actionbase.server.api.graph.v2.service

import com.kakao.actionbase.server.util.NameValidator
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.AliasEntity
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.AliasUpdateRequest
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCopyRequest

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

@RestController
@Validated
class ServiceAliasController(
    val graph: Graph,
) {
    @GetMapping("/graph/v2/service/{service}/alias/{alias}")
    fun get(
        @PathVariable service: String,
        @PathVariable alias: String,
    ): Mono<ResponseEntity<AliasEntity>> {
        val name = EntityName(service, alias)
        return graph.aliasDdl
            .getSingle(name)
            .map { it.withLabel(graph) }
            .mapToResponseEntity()
    }

    @GetMapping("/graph/v2/service/{service}/alias")
    fun getAll(
        @PathVariable service: String,
    ): Mono<ResponseEntity<DdlPage<AliasEntity>>> =
        graph.aliasDdl
            .getAll(EntityName(service))
            .map { page ->
                page.copy(content = page.content.map { it.withLabel(graph) })
            }.mapToResponseEntity()

    @PostMapping("/graph/v2/service/{service}/alias/{alias}")
    fun create(
        @PathVariable service: String,
        @PathVariable alias: String,
        @Valid @RequestBody request: AliasCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<AliasEntity>>> {
        val name = EntityName(NameValidator.validate(service, "service"), NameValidator.validate(alias, "alias"))
        return graph.aliasDdl
            .create(name, request)
            .map {
                it.copy(result = it.result?.withLabel(graph))
            }.mapToResponseEntity()
    }

    @PutMapping("/graph/v2/service/{service}/alias/{alias}")
    fun update(
        @PathVariable service: String,
        @PathVariable alias: String,
        @RequestBody request: AliasUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<AliasEntity>>> {
        val name = EntityName(service, alias)
        return graph.aliasDdl
            .update(name, request)
            .map {
                it.copy(result = it.result?.withLabel(graph))
            }.mapToResponseEntity()
    }

    @DeleteMapping("/graph/v2/service/{service}/alias/{alias}")
    fun delete(
        @PathVariable service: String,
        @PathVariable alias: String,
    ): Mono<ResponseEntity<DdlStatus<AliasEntity>>> {
        val name = EntityName(service, alias)
        return graph.aliasDdl
            .delete(name, AliasDeleteRequest())
            .map {
                it.copy(result = it.result?.withLabel(graph))
            }.mapToResponseEntity()
    }

    @PostMapping("/graph/v2/service/{service}/alias/{alias}/new-label")
    fun newLabel(
        @PathVariable service: String,
        @PathVariable alias: String,
        @Valid @RequestBody request: LabelCopyRequest,
    ): Mono<ResponseEntity<DdlStatus<LabelEntity>>> {
        val name = EntityName(service, alias)
        return graph.aliasDdl
            .getSingle(name)
            .map { it.withLabel(graph) }
            .flatMap {
                val from = it.label?.name
                if (from != null) {
                    val to = EntityName(service, request.target)
                    graph.labelDdl.copy(from, to, mapOf("storage" to request.storage)).mapToResponseEntity()
                } else {
                    Mono.just(ResponseEntity.badRequest().build())
                }
            }
    }
}
