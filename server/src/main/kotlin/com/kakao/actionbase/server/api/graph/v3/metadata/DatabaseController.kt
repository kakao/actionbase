package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlPage
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus

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
    ): Mono<ResponseEntity<DdlPage<DatabaseResponse>>> = v3CompatService.getDatabases(status).mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}")
    fun getDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<DatabaseResponse>> = v3CompatService.getDatabase(database).mapToResponseEntity()

    @PostMapping("/graph/v3/databases")
    fun createDatabase(
        @Valid @RequestBody request: DatabaseCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<DatabaseResponse>>> =
        v3CompatService
            .createDatabase(V3NameValidator.validateDatabase(request.database), request)
            .mapToResponseEntity()

    @PutMapping("/graph/v3/databases/{database}")
    fun updateDatabase(
        @PathVariable database: String,
        @Valid @RequestBody request: DatabaseUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<DatabaseResponse>>> = v3CompatService.updateDatabase(database, request).mapToResponseEntity()

    @DeleteMapping("/graph/v3/databases/{database}")
    fun deleteDatabase(
        @PathVariable database: String,
    ): Mono<ResponseEntity<DdlStatus<DatabaseResponse>>> = v3CompatService.deleteDatabase(database).mapToResponseEntity()
}
