package com.kakao.actionbase.server.api.graph.v2.service

import com.kakao.actionbase.server.configuration.Defaults
import com.kakao.actionbase.server.payload.EdgesRequest
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.label.DeleteEdgeRequest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class ServiceLabelEdgeDeleteController(
    val graph: Graph,
) {
    @PostMapping("/graph/v2/service/{service}/label/{label}/edge/delete")
    fun delete(
        @PathVariable service: String,
        @PathVariable label: String,
        @RequestBody edges: EdgesRequest,
        @RequestHeader(
            Defaults.REQUEST_ID_HEADER,
            required = false,
            defaultValue = Defaults.DEFAULT_REQUEST_ID,
        ) requestId: String,
    ): Mono<ResponseEntity<MutationResult>> {
        val request = DeleteEdgeRequest("$service.$label", edges.edges, edges.audit, requestId)
        return graph.delete(request).mapToResponseEntity()
    }
}
