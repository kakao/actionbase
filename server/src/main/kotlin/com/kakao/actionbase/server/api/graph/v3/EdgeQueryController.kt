package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeCountPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeCountPayload
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.server.payload.EdgeQueryGetRequest
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * ranges: pre-filter for low level scan
 * filters: post-filter for DataFrame
 */
@RestController
class EdgeQueryController(
    private val queryService: QueryService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/count")
    fun count(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam start: String,
        @RequestParam direction: Direction,
        @RequestParam ranges: String? = null,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
    ): Mono<ResponseEntity<EdgeCountPayload>> =
        queryService
            .count(database, table, start, direction, ranges, filters, features)
            .mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/counts")
    fun counts(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam start: List<String>,
        @RequestParam direction: Direction,
        @RequestParam ranges: String? = null,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
    ): Mono<ResponseEntity<DataFrameEdgeCountPayload>> =
        queryService
            .counts(database, table, start, direction, ranges, filters, features)
            .mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/get")
    fun get(
        @PathVariable database: String,
        @PathVariable table: String,
        @ModelAttribute request: EdgeQueryGetRequest,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .gets(database, table, request.source, request.target, request.ranges, request.filters, request.features)
            .mapToResponseEntity()

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/edges/get")
    fun getByPost(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: EdgeQueryGetRequest,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .gets(database, table, request.source, request.target, request.ranges, request.filters, request.features)
            .mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/scan/{index}")
    fun scan(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable index: String,
        @RequestParam start: String,
        @RequestParam direction: Direction,
        @RequestParam limit: Int = ScanFilter.defaultLimit,
        @RequestParam offset: String? = null,
        @RequestParam ranges: String? = null,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .scan(database, table, index, start, direction, limit, offset, ranges, filters, features)
            .mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/seek/{cache}")
    fun seek(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable cache: String,
        @RequestParam start: String,
        @RequestParam direction: Direction,
        @RequestParam limit: Int = ScanFilter.defaultLimit,
        @RequestParam offset: String? = null,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .seek(database, table, cache, start, direction, limit, offset)
            .mapToResponseEntity()

    @GetMapping("/graph/v3/databases/{database}/tables/{table}/edges/agg/{group}")
    fun agg(
        @PathVariable database: String,
        @PathVariable table: String,
        @PathVariable group: String,
        @RequestParam start: List<String>,
        @RequestParam direction: Direction,
        @RequestParam ranges: String,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
        @RequestParam ttl: Long? = null,
    ): Mono<ResponseEntity<DataFrameEdgeAggPayload>> =
        queryService
            .agg(database, table, group, start, direction, ranges, filters, features, ttl)
            .mapToResponseEntity()
}
