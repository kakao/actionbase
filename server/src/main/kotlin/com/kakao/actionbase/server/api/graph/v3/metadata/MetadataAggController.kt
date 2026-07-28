package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationItemRequest
import com.kakao.actionbase.core.edge.payload.AggregationSweepRequest
import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.AggregationsSweepResponse
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.payload.AggregationsListResponse
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
    @GetMapping("/aggregations/v1/metadata")
    fun getAggregations(
        @RequestParam(required = false) type: AggregationType?,
    ): ResponseEntity<AggregationsListResponse> {
        val aggregations = aggregationService.getAggregations(type)

        return ResponseEntity.ok(AggregationsListResponse.of(aggregations))
    }

    @PostMapping("/aggregations/v1/aggregate")
    fun aggregate(
        @RequestBody request: AggregationItemRequest,
    ): Mono<ResponseEntity<AggregationsItemResponse>> =
        aggregationService
            .aggregate(items = request.items)
            .map { results -> ResponseEntity.ok(AggregationsItemResponse.from(results)) }

    @PostMapping("/aggregations/v1/sweep")
    fun sweep(
        @RequestBody request: AggregationSweepRequest,
    ): Mono<ResponseEntity<AggregationsSweepResponse>> =
        aggregationService
            .sweep(items = request.items)
            .map { results -> ResponseEntity.ok(AggregationsSweepResponse.from(results)) }
}
