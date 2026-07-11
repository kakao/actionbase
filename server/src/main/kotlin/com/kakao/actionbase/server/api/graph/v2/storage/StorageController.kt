package com.kakao.actionbase.server.api.graph.v2.storage

import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.StorageUpdateRequest

import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

// Storage creation is removed as part of the v2 Storage removal (#398): new
// labels reference storage via datastore:// URIs, so no new Storage is minted.
// Read and update of existing storages stay available.
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
    @PutMapping("/graph/v2/storage/{storage}")
    fun update(
        @RequestHeader headers: HttpHeaders,
        @PathVariable storage: String,
        @RequestParam(required = false) sync: Boolean = false,
        @RequestBody request: StorageUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<StorageEntity>>> {
        val name = EntityName.fromOrigin(storage)
        return graph.storageDdl.update(name, request).mapToResponseEntity()
    }
}
