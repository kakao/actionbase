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
class TableController(
    private val v3CompatService: V3CompatService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables")
    fun listTables(
        @PathVariable database: String,
        @RequestParam(required = false, defaultValue = "ACTIVE") status: MetadataStatus,
    ): Mono<ResponseEntity<DdlPage<TableResponse>>> = v3CompatService.getTables(database, status).mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}")
    fun getTable(
        @PathVariable database: String,
        @PathVariable table: String,
    ): Mono<ResponseEntity<TableResponse>> = v3CompatService.getTable(database, table).mapToResponseEntity()

    @PostMapping("/graph/v3/databases/{database}/tables")
    fun createTable(
        @PathVariable database: String,
        @Valid @RequestBody request: TableCreateRequest,
    ): Mono<ResponseEntity<DdlStatus<TableResponse>>> =
        v3CompatService
            .createTable(
                V3NameValidator.validateDatabase(database),
                V3NameValidator.validateTable(request.table),
                request,
            ).mapToResponseEntity()

    @PutMapping("/graph/v3/databases/{database}/tables/{table}")
    fun updateTable(
        @PathVariable database: String,
        @PathVariable table: String,
        @Valid @RequestBody request: TableUpdateRequest,
    ): Mono<ResponseEntity<DdlStatus<TableResponse>>> = v3CompatService.updateTable(database, table, request).mapToResponseEntity()

    @DeleteMapping("/graph/v3/databases/{database}/tables/{table}")
    fun deleteTable(
        @PathVariable database: String,
        @PathVariable table: String,
    ): Mono<ResponseEntity<DdlStatus<TableResponse>>> = v3CompatService.deleteTable(database, table).mapToResponseEntity()
}
