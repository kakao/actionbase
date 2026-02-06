package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.TableDescriptor

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
class TableController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables")
    fun listTables(
        @PathVariable database: String,
    ): Mono<ResponseEntity<List<TableDescriptor<*>>>> =
        v3CompatService
            .getTables(V3NameValidator.validateDatabase(database))
            .map { ResponseEntity.ok(it) }

    @GetMapping("/graph/v3/databases/{database}/tables/{table}")
    fun getTable(
        @PathVariable database: String,
        @PathVariable table: String,
    ): Mono<ResponseEntity<TableDescriptor<*>>> =
        v3CompatService
            .getTable(V3NameValidator.validateDatabase(database), V3NameValidator.validateTable(table))
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/graph/v3/databases/{database}/tables")
    fun createTable(
        @PathVariable database: String,
        @Valid @RequestBody request: TableCreateRequest,
    ): Mono<ResponseEntity<TableDescriptor<*>>> =
        v3CompatService
            .createTable(
                V3NameValidator.validateDatabase(database),
                V3NameValidator.validateTable(request.table),
                request,
            ).map { ResponseEntity.ok(it) }

    @PutMapping("/graph/v3/databases/{database}/tables/{table}")
    fun updateTable(
        @PathVariable database: String,
        @PathVariable table: String,
        @Valid @RequestBody request: TableUpdateRequest,
    ): Mono<ResponseEntity<TableDescriptor<*>>> =
        v3CompatService
            .updateTable(
                V3NameValidator.validateDatabase(database),
                V3NameValidator.validateTable(table),
                request,
            ).map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/graph/v3/databases/{database}/tables/{table}")
    fun deleteTable(
        @PathVariable database: String,
        @PathVariable table: String,
    ): Mono<ResponseEntity<Void>> =
        v3CompatService
            .deleteTable(V3NameValidator.validateDatabase(database), V3NameValidator.validateTable(table))
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
}
