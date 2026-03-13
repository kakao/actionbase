package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest

import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import jakarta.validation.Valid
import reactor.core.publisher.Mono

@RestController
@Validated
@RequestMapping
class DatabaseController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping("/graph/v3/databases")
    fun listDatabases(
        @RequestParam(required = false, defaultValue = "ACTIVE") status: MetadataStatus,
    ): Mono<ResponseEntity<List<DatabaseDescriptor>>> =
        v3CompatService
            .getDatabases(status)
            .map { ResponseEntity.ok(it) }

    @GetMapping("/graph/v3/databases/{database}")
    fun getDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .getDatabase(V3NameValidator.validateDatabase(database))
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/graph/v3/databases")
    fun createDatabase(
        @Valid @RequestBody request: DatabaseCreateRequest,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .createDatabase(V3NameValidator.validateDatabase(request.database), request)
            .map { ResponseEntity.ok(it) }

    @PutMapping("/graph/v3/databases/{database}")
    fun updateDatabase(
        @PathVariable database: String,
        @Valid @RequestBody request: DatabaseUpdateRequest,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .updateDatabase(V3NameValidator.validateDatabase(database), request)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/graph/v3/databases/{database}")
    fun deleteDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<Void>> =
        v3CompatService
            .deleteDatabase(V3NameValidator.validateDatabase(database))
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
}
