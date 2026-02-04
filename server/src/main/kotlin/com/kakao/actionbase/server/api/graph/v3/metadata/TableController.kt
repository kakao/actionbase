package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.TableDescriptor

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
@RequestMapping("/graph/v3/databases/{database}/tables")
class TableController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping
    fun listTables(
        @PathVariable database: String,
    ): Mono<ResponseEntity<List<TableDescriptor.Edge>>> =
        v3CompatService
            .getTables(database)
            .map { ResponseEntity.ok(it) }

    @GetMapping("/{table}")
    fun getTable(
        @PathVariable database: String,
        @PathVariable table: String,
    ): Mono<ResponseEntity<TableDescriptor.Edge>> =
        v3CompatService
            .getTable(database, table)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/{table}")
    fun createTable(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: TableCreateRequest,
    ): Mono<ResponseEntity<TableDescriptor.Edge>> =
        v3CompatService
            .createTable(database, table, request)
            .map { ResponseEntity.ok(it) }

    @PutMapping("/{table}")
    fun updateTable(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: TableUpdateRequest,
    ): Mono<ResponseEntity<TableDescriptor.Edge>> =
        v3CompatService
            .updateTable(database, table, request)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())
}
