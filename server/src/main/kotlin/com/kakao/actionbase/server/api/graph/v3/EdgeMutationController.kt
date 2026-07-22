package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.server.payload.EdgeScanDeleteResponse
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.core.metadata.Direction

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class EdgeMutationController(
    private val mutationService: MutationService,
) {
    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges")
    fun mutateEdge(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<EdgeMutationResponse>> =
        mutationService
            .mutate(database, table, request.mutations, lock, syncMode = null, requestContext = requestContext)
            .map { ResponseEntity.ok(EdgeMutationResponse.from(it)) }

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges/sync")
    fun mutateEdgeSync(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeBulkMutationRequest,
        @RequestParam(required = false) lock: Boolean = true,
        @RequestParam(required = false) force: Boolean = false,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<EdgeMutationResponse>> =
        mutationService
            .mutate(database, table, request.mutations, lock, syncMode = MutationMode.SYNC, forceSyncMode = force, requestContext = requestContext)
            .map { ResponseEntity.ok(EdgeMutationResponse.from(it)) }

    @DeleteMapping("/graph/v3/databases/{database}/tables/{table}/edges/scan/{index}")
    fun scanDelete(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable index: String,
        @RequestParam start: String,
        @RequestParam direction: Direction,
        @RequestParam limit: Int,
        @RequestParam ranges: String? = null,
    ): Mono<ResponseEntity<EdgeScanDeleteResponse>> =
        mutationService
            .scanDelete(database, table, index, start, direction, limit, ranges)
            .map { EdgeScanDeleteResponse(database, table, index, it) }
            .mapToResponseEntity()
}
