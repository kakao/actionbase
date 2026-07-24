package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.engine.service.AggregationQueryService
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class MetadataAggQueryController(
    private val aggregationQueryService: AggregationQueryService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/aggregations/topk/{topk}")
    fun topk(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable topk: String,
        @RequestParam entity: String,
        @RequestParam limit: Int = ScanFilter.defaultLimit,
        @RequestParam offset: String? = null,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        aggregationQueryService
            .topk(database, table, topk, entity, limit, offset)
            .mapToResponseEntity()
}
