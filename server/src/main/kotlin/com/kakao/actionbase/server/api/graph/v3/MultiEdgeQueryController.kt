package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.DataFrameMultiEdgeAggCountPayload
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.server.payload.MultiEdgeIdsRequest
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.core.metadata.Direction

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
    private val queryService: QueryService,
) {
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/ids")
    fun ids(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam ids: List<Any>,
        @RequestParam filters: String? = null,
        @RequestParam features: List<String> = emptyList(),
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .gets(database, table, ids, filters, features)
            .mapToResponseEntity()

    @PostMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/ids")
    fun idsByPost(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestBody request: MultiEdgeIdsRequest,
    ): Mono<ResponseEntity<DataFrameEdgePayload>> =
        queryService
            .gets(database, table, request.ids, request.filters, request.features)
            .mapToResponseEntity()

    /**
     * Returns the count of multi-edges for each (source, target) pair.
     * Internally delegates to the agg API using the group defined in the table schema.
     * The counter field (_target for OUT, _source for IN) is automatically prepended to ranges.
     */
    @GetMapping("/graph/v3/databases/{database}/tables/{table}/multi-edges/count")
    fun count(
        @PathVariable database: String,
        @PathVariable table: String,
        @RequestParam group: String = EdgeMutationBuilder.MULTI_EDGE_COUNT_GROUP_NAME,
        @RequestParam start: List<String>,
        @RequestParam target: String,
        @RequestParam direction: Direction,
        @RequestParam ranges: String? = null,
        @RequestParam ttl: Long? = null,
    ): Mono<ResponseEntity<DataFrameMultiEdgeAggCountPayload>> =
        queryService
            .multiEdgeCount(database, table, group, start, direction, target, ranges, ttl)
            .mapToResponseEntity()
}
