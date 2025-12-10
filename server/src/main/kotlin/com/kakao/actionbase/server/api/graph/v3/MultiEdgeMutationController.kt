package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationResponse
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
class MultiEdgeMutationController(
    private val v3MutationService: V3MutationService,
) {
    @PostMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges")
    fun mutateMultiEdge(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: MultiEdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MultiEdgeMutationResponse>> =
        // Note: Multi-edges are not supported in AsyncProcessor.
        // Forces SYNC processing regardless of the table's ASYNC setting.
        v3MutationService
            .mutateMultiEdge(database, table, request, lock, sync = MutationMode.SYNC, requestContext)
            .map { ResponseEntity.ok(it) }

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/sync")
    fun mutateMultiEdgeSync(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: MultiEdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MultiEdgeMutationResponse>> =
        v3MutationService
            .mutateMultiEdge(database, table, request, lock, sync = MutationMode.SYNC, requestContext)
            .map { ResponseEntity.ok(it) }
}
