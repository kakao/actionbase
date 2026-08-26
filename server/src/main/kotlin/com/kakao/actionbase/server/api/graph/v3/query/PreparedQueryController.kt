package com.kakao.actionbase.server.api.graph.v3.query

import com.kakao.actionbase.engine.service.MetadataStatus
import com.kakao.actionbase.engine.service.PreparedQueryAliasDescriptor
import com.kakao.actionbase.engine.service.PreparedQueryDescriptor
import com.kakao.actionbase.engine.service.PreparedQueryService
import com.kakao.actionbase.v2.engine.entity.EntityName

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import jakarta.validation.Valid
import reactor.core.publisher.Mono

/**
 * Running a query lives on the query path (`POST /graph/v3/query/{id}`), so that reading one and running
 * it never look alike. These paths sit under `/graph/v3` rather than a database, because a query names its
 * own databases per step.
 */
@RestController
class PreparedQueryController(
    private val preparedQueryService: PreparedQueryService,
) {
    private val tenant: String
        get() = EntityName.tenant

    @GetMapping("/graph/v3/prepared-queries")
    fun listQueries(
        @RequestParam(required = false, defaultValue = "ACTIVE") status: MetadataStatus,
    ): Mono<ResponseEntity<List<PreparedQueryResponse>>> =
        preparedQueryService
            .list(status)
            .map { queries -> ResponseEntity.ok(queries.map { it.toResponse() }) }

    /** [id] takes either form: the id a registration was given, or a name pointing at one. */
    @GetMapping("/graph/v3/prepared-queries/{id}")
    fun getQuery(
        @PathVariable id: String,
    ): Mono<ResponseEntity<PreparedQueryResponse>> =
        preparedQueryService
            .get(id)
            .map { ResponseEntity.ok(it.toResponse()) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/graph/v3/prepared-queries")
    fun createQuery(
        @Valid @RequestBody request: PreparedQueryCreateRequest,
    ): Mono<ResponseEntity<PreparedQueryResponse>> =
        preparedQueryService
            .register(
                desc = request.comment,
                arguments = request.arguments.map { it.toStructField() },
                fetch = request.fetch,
                transform = request.transform,
                stats = request.stats,
            ).map { ResponseEntity.ok(it.toResponse()) }

    @PutMapping("/graph/v3/prepared-queries/{id}")
    fun updateQuery(
        @PathVariable id: String,
        @Valid @RequestBody request: PreparedQueryUpdateRequest,
    ): Mono<ResponseEntity<PreparedQueryResponse>> =
        preparedQueryService
            .amend(
                id = id,
                desc = request.comment,
                arguments = request.arguments?.map { it.toStructField() },
                fetch = request.fetch,
                transform = request.transform,
                stats = request.stats,
            ).map { ResponseEntity.ok(it.toResponse()) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/graph/v3/prepared-queries/{id}")
    fun deleteQuery(
        @PathVariable id: String,
    ): Mono<ResponseEntity<Void>> =
        preparedQueryService
            .delete(id)
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))

    @GetMapping("/graph/v3/prepared-queries/aliases")
    fun listAliases(
        @RequestParam(required = false, defaultValue = "ACTIVE") status: MetadataStatus,
    ): Mono<ResponseEntity<List<PreparedQueryAliasResponse>>> =
        preparedQueryService
            .aliases(status)
            .map { aliases -> ResponseEntity.ok(aliases.map { it.toResponse() }) }

    @GetMapping("/graph/v3/prepared-queries/aliases/{alias}")
    fun getAlias(
        @PathVariable alias: String,
    ): Mono<ResponseEntity<PreparedQueryAliasResponse>> =
        preparedQueryService
            .alias(alias)
            .map { ResponseEntity.ok(it.toResponse()) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @PostMapping("/graph/v3/prepared-queries/aliases")
    fun createAlias(
        @Valid @RequestBody request: PreparedQueryAliasCreateRequest,
    ): Mono<ResponseEntity<PreparedQueryAliasResponse>> =
        preparedQueryService
            .createAlias(request.alias, request.comment, request.target)
            .map { ResponseEntity.ok(it.toResponse()) }

    @PutMapping("/graph/v3/prepared-queries/aliases/{alias}")
    fun updateAlias(
        @PathVariable alias: String,
        @Valid @RequestBody request: PreparedQueryAliasUpdateRequest,
    ): Mono<ResponseEntity<PreparedQueryAliasResponse>> =
        preparedQueryService
            .updateAlias(alias, request.comment, request.target, request.active)
            .map { ResponseEntity.ok(it.toResponse()) }
            .defaultIfEmpty(ResponseEntity.notFound().build())

    @DeleteMapping("/graph/v3/prepared-queries/aliases/{alias}")
    fun deleteAlias(
        @PathVariable alias: String,
    ): Mono<ResponseEntity<Void>> =
        preparedQueryService
            .deleteAlias(alias)
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))

    private fun PreparedQueryDescriptor.toResponse(): PreparedQueryResponse =
        PreparedQueryResponse(
            tenant = tenant,
            id = id,
            arguments = arguments,
            fetch = fetch,
            transform = transform,
            stats = stats,
            active = active,
            comment = desc,
        )

    private fun PreparedQueryAliasDescriptor.toResponse(): PreparedQueryAliasResponse =
        PreparedQueryAliasResponse(
            tenant = tenant,
            alias = alias,
            target = target,
            active = active,
            comment = desc,
        )
}
