package com.kakao.actionbase.server.api.graph.v2.storage

import com.kakao.actionbase.server.util.NameValidator
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.StorageCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.StorageUpdateRequest

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class StorageController(
    val graph: Graph,
) {
    @Suppress("UnusedParameter")
    @GetMapping("/graph/v2/storage/{storage}")
    fun get(
        @RequestHeader headers: HttpHeaders,
        @PathVariable storage: String,
    ): Mono<ResponseEntity<StorageEntity>> {
        val name = EntityName.fromOrigin(storage)
        return graph.storageDdl.getSingle(name).mapToResponseEntity()
    }

    @Suppress("UnusedParameter")
    @GetMapping("/graph/v2/storage")
    fun getAll(
        @RequestHeader headers: HttpHeaders,
        pageable: Pageable = Pageable.unpaged(),
    ): Mono<ResponseEntity<DdlPage<StorageEntity>>> = graph.storageDdl.getAll(EntityName.origin).mapToResponseEntity()

    @Suppress("UnusedParameter")
    @PostMapping("/graph/v2/storage/{storage}")
    fun create(
        @RequestHeader headers: HttpHeaders,
        @PathVariable storage: String,
        @RequestBody request: StorageCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<StorageEntity>>> {
        val name = EntityName.fromOrigin(NameValidator.validate(storage, "storage"))
        return graph.storageDdl.create(name, request).mapToResponseEntity()
    }

    @Suppress("UnusedParameter")
    @PutMapping("/graph/v2/storage/{storage}")
    fun update(
        @RequestHeader headers: HttpHeaders,
        @PathVariable storage: String,
        @RequestParam(required = false) sync: Boolean = false,
        @RequestBody request: StorageUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<StorageEntity>>> {
        val name = EntityName.fromOrigin(NameValidator.validate(storage, "storage"))
        return graph.storageDdl.update(name, request).mapToResponseEntity()
    }
}
