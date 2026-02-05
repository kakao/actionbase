package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.server.payload.MultiEdgeIdsRequest
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.v3.V3QueryService

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class MultiEdgeQueryController(
    private val v3QueryService: V3QueryService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/ids")
    fun ids(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam ids: List<Any>,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        v3QueryService
            .gets(database, table, ids, filters, features)
            .mapToResponseEntity()

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/ids")
    fun idsByPost(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: MultiEdgeIdsRequest,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        v3QueryService
            .gets(database, table, request.ids, request.filters, request.features)
            .mapToResponseEntity()
}
