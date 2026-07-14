package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationItemRequest
import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.metadata.payload.AggregationsListResponse
import com.kakao.actionbase.core.metadata.payload.ExpireTablesResponse
import com.kakao.actionbase.engine.service.AggregationService

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class MetadataAggController(
    private val aggregationService: AggregationService,
) {
    @GetMapping("/graph/v3/aggregations")
    fun getAggregations(
        @RequestParam(required = false) type: AggregationType?,
    ): ResponseEntity<AggregationsListResponse> {
        val aggregations = aggregationService.getAggregations()

        return ResponseEntity.ok(AggregationsListResponse.of(type, metadata = aggregations))
    }

    @GetMapping("/graph/v3/aggregations/expires")
    fun getExpireTables(): ResponseEntity<ExpireTablesResponse> =
        ResponseEntity.ok(ExpireTablesResponse(tables = aggregationService.getExpireTables()))

    @PostMapping("/graph/v3/aggregations")
    fun aggregations(
        @RequestBody aggregationItemRequest: AggregationItemRequest,
    ): Mono<ResponseEntity<AggregationsItemResponse>> =
        aggregationService
            .aggregate(
                type = aggregationItemRequest.type,
                items = aggregationItemRequest.items,
            )
            .map { results -> ResponseEntity.ok(AggregationsItemResponse.from(results)) }

    @PostMapping("/graph/v3/aggregations/sweep")
    fun sweep(
        @RequestParam type: AggregationType,
        @RequestParam expireDatabase: String,
        @RequestParam expireTable: String,
        @RequestParam partition: Long,
        @RequestParam now: Long,
    ): Mono<ResponseEntity<AggregationsItemResponse>> =
        aggregationService
            .sweep(type = type, expireDatabase = expireDatabase, expireTable = expireTable, partition = partition, now = now)
            .map { results -> ResponseEntity.ok(AggregationsItemResponse.from(results)) }
}
