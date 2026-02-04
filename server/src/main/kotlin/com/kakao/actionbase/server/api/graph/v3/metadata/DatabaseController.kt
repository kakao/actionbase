package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
@RequestMapping("/graph/v3/databases")
class DatabaseController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping
    fun listDatabases(): Mono<ResponseEntity<List<DatabaseDescriptor>>> =
        v3CompatService
            .getDatabases()
            .map { ResponseEntity.ok(it) }

    @GetMapping("/{database}")
    fun getDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .getDatabase(database)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/{database}")
    fun createDatabase(
        @PathVariable database: String,
        @RequestBody request: DatabaseCreateRequest,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .createDatabase(database, request)
            .map { ResponseEntity.ok(it) }

    @PutMapping("/{database}")
    fun updateDatabase(
        @PathVariable database: String,
        @RequestBody request: DatabaseUpdateRequest,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .updateDatabase(database, request)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/{database}")
    fun deleteDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<DatabaseDescriptor>> =
        v3CompatService
            .deleteDatabase(database)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())
}
