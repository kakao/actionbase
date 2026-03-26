package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.sql.QueryResult
import com.kakao.actionbase.v2.engine.sql.toNamedJsonFormat

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueryController(
    val graph: Graph,
) {
    @PostMapping("/graph/v3/query")
    fun query(
        @RequestBody actionBaseQuery: ActionbaseQuery,
    ): Mono<out ResponseEntity<out NamedQueryResult>> =
        graph
            .query(actionBaseQuery)
            .map {
                val items = it.map { entry -> entry.value.toNamedJsonFormat(entry.key) }
                NamedQueryResult(items)
            }.mapToResponseEntity()
}

data class NamedQueryResult(
    val items: List<QueryResult.NamedJsonFormat>,
)
