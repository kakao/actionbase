package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsTopkResponse
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
    @GetMapping("/aggregations/v1/databases/{database}/tables/{table}/topks/{topk}")
    fun topk(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable topk: String,
        @RequestParam entity: String,
        @RequestParam(required = false) dimensionValues: String? = null,
        @RequestParam limit: Int = ScanFilter.defaultLimit,
        @RequestParam offset: String? = null,
    ): Mono<ResponseEntity<AggregationsTopkResponse>> =
        aggregationQueryService
            .topk(
                database = database,
                table = table,
                topk = topk,
                entity = entity,
                dimensionValues = dimensionValues?.split("|")?.filter { it.isNotEmpty() } ?: emptyList(),
                limit = limit,
                offset = offset,
            ).map(AggregationsTopkResponse::from)
            .mapToResponseEntity()
}
