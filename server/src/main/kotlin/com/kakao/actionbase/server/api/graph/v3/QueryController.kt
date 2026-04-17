package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.sql.QueryResult

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueryController(
    private val queryService: QueryService,
) {
    @PostMapping("/graph/v3/query")
    fun query(
        @RequestBody actionBaseQuery: ActionbaseQuery,
    ): Mono<ResponseEntity<NamedQueryResult>> =
        queryService
            .query(actionBaseQuery)
            .map {
                val items = it.map { entry -> entry.value.toNamedJsonFormat(entry.key) }
                NamedQueryResult(items)
            }.mapToResponseEntity()

    private fun DataFrame.toNamedJsonFormat(name: String): QueryResult.NamedJsonFormat {
        val meta = schema.fields.map { QueryResult.Meta(it.name, it.type.name) }
        val data = rows.map { it.data }
        return QueryResult.NamedJsonFormat(
            name = name,
            meta = meta,
            data = data,
            rows = data.size,
            rowsBeforeLimitAtLeast = data.size,
            statistics = QueryResult.Statistics(0.0, 0, 0),
            stats = emptyList(),
            offset = offset,
            hasNext = hasNext,
        )
    }
}
