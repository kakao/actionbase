package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.vertex.payload.DataFrameVertexPayload
import com.kakao.actionbase.engine.service.QueryService

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class VertexQueryController(
    private val queryService: QueryService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/vertices/get")
    fun getVertices(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam key: List<String>,
        @RequestParam(required = false) filters: String? = null,
    ): Mono<ResponseEntity<DataFrameVertexPayload>> =
        queryService
            .getVertices(database, table, key, filters)
            .map { ResponseEntity.ok(DataFrameVertexPayload.from(it)) }
}
