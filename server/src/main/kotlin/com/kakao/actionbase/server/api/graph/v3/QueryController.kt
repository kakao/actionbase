package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.server.util.mapToResponseEntity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueryController(
    private val queryEngine: QueryEngine,
) {
    @PostMapping("/graph/v3/query")
    fun query(
        @RequestBody actionBaseQuery: ActionbaseQuery,
    ): Mono<ResponseEntity<NamedQueryResult>> =
        queryEngine
            .query(actionBaseQuery)
            .map { NamedQueryResult(it) }
            .mapToResponseEntity()
}
