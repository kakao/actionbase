package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.AliasDescriptor

import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import jakarta.validation.Valid
import reactor.core.publisher.Mono

@RestController
@Validated
@RequestMapping
class AliasController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping("/graph/v3/databases/{database}/aliases")
    fun listAliases(
        @PathVariable database: String,
    ): Mono<ResponseEntity<List<AliasDescriptor>>> =
        v3CompatService
            .getAliases(V3NameValidator.validateDatabase(database))
            .map { ResponseEntity.ok(it) }

    @GetMapping("/graph/v3/databases/{database}/aliases/{alias}")
    fun getAlias(
        @PathVariable database: String,
        @PathVariable alias: String,
    ): Mono<ResponseEntity<AliasDescriptor>> =
        v3CompatService
            .getAlias(V3NameValidator.validateDatabase(database), V3NameValidator.validateAlias(alias))
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/graph/v3/databases/{database}/aliases")
    fun createAlias(
        @PathVariable database: String,
        @Valid @RequestBody request: AliasCreateRequest,
    ): Mono<ResponseEntity<AliasDescriptor>> =
        v3CompatService
            .createAlias(
                V3NameValidator.validateDatabase(database),
                V3NameValidator.validateAlias(request.alias),
                request,
            ).map { ResponseEntity.ok(it) }

    @PutMapping("/graph/v3/databases/{database}/aliases/{alias}")
    fun updateAlias(
        @PathVariable database: String,
        @PathVariable alias: String,
        @Valid @RequestBody request: AliasUpdateRequest,
    ): Mono<ResponseEntity<AliasDescriptor>> =
        v3CompatService
            .updateAlias(
                V3NameValidator.validateDatabase(database),
                V3NameValidator.validateAlias(alias),
                request,
            ).map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/graph/v3/databases/{database}/aliases/{alias}")
    fun deleteAlias(
        @PathVariable database: String,
        @PathVariable alias: String,
    ): Mono<ResponseEntity<Void>> =
        v3CompatService
            .deleteAlias(V3NameValidator.validateDatabase(database), V3NameValidator.validateAlias(alias))
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
}
