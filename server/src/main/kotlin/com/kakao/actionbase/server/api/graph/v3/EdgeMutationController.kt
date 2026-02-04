package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.v3.V3MutationService

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class EdgeMutationController(
    private val v3MutationService: V3MutationService,
) {
    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges")
    fun mutateEdge(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<EdgeMutationResponse>> =
        v3MutationService
            .mutateEdge(database, table, request, lock, mode = null, internal = null, requestContext)
            .map { ResponseEntity.ok(it) }

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges/sync")
    fun mutateEdgeSync(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<EdgeMutationResponse>> =
        v3MutationService
            .mutateEdge(database, table, request, lock, mode = MutationMode.SYNC, internal = null, requestContext)
            .map { ResponseEntity.ok(it) }

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges/internal/sync")
    fun mutateEdgeInternalSync(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<EdgeMutationResponse>> =
        v3MutationService
            .mutateEdge(database, table, request, lock, mode = null, internal = MutationMode.SYNC, requestContext)
            .map { ResponseEntity.ok(it) }
}
