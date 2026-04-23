package com.kakao.actionbase.server.api.graph.v2.edge

import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.label.DeleteEdgeRequest
import com.kakao.actionbase.v2.engine.label.DeleteIdEdgeRequest
import com.kakao.actionbase.v2.engine.label.InsertEdgeRequest
import com.kakao.actionbase.v2.engine.label.InsertIdEdgeRequest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class EdgeController(
    val graph: Graph,
) {
    @PostMapping("/graph/v2/edge")
    fun insert(
        @RequestParam(required = false) bulk: Boolean = false,
        @RequestParam(required = false) mode: MutationMode?,
        @RequestBody request: InsertEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.upsert(request, bulk, mode, requestContext::newCollector).mapToResponseEntity()

    @PutMapping("/graph/v2/edge")
    fun update(
        @RequestParam(required = false) bulk: Boolean = false,
        @RequestParam(required = false) mode: MutationMode?,
        @RequestBody request: InsertEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.update(request, bulk, mode, requestContext::newCollector).mapToResponseEntity()

    @DeleteMapping("/graph/v2/edge")
    fun delete(
        @RequestParam(required = false) bulk: Boolean = false,
        @RequestParam(required = false) mode: MutationMode?,
        @RequestBody request: DeleteEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.delete(request, bulk, mode, requestContext::newCollector).mapToResponseEntity()

    @PostMapping("/graph/v2/edge/id")
    fun insertId(
        @RequestBody request: InsertIdEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.upsert(request, requestContext::newCollector).mapToResponseEntity()

    @PutMapping("/graph/v2/edge/id")
    fun updateId(
        @RequestBody request: InsertIdEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.update(request, requestContext::newCollector).mapToResponseEntity()

    @DeleteMapping("/graph/v2/edge/id")
    fun deleteId(
        @RequestBody request: DeleteIdEdgeRequest,
        requestContext: RequestContext,
    ): Mono<ResponseEntity<MutationResult>> = graph.delete(request, requestContext::newCollector).mapToResponseEntity()
}
