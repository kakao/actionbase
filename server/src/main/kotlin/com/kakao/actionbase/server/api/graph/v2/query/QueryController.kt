package com.kakao.actionbase.server.api.graph.v2.query

import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.sql.QueryResult
import com.kakao.actionbase.v2.engine.sql.toJsonFormat

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueryController(
    val graph: Graph,
) {
    @PostMapping("/graph/v2/query")
    fun queryV2(
        @RequestBody actionBaseQuery: ActionbaseQuery,
    ): Mono<out ResponseEntity<out NamedQueryResultV2>> =
        graph
            .query(actionBaseQuery)
            .map {
                val items = it.map { entry -> NamedQueryResultV2Item(entry.key, entry.value.toJsonFormat()) }
                NamedQueryResultV2(items)
            }.mapToResponseEntity()
}

data class NamedQueryResultV2(
    val result: List<NamedQueryResultV2Item>,
)

data class NamedQueryResultV2Item(
    val name: String,
    val data: QueryResult.OutputFormat,
)
