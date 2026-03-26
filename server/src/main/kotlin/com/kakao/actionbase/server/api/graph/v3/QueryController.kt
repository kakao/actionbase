package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.server.util.mapToResponseEntity
import com.kakao.actionbase.v2.engine.sql.toNamedJsonFormat
import com.kakao.actionbase.v2.engine.v3.V3QueryService

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

@RestController
class QueryController(
    private val v3QueryService: V3QueryService,
) {
    @PostMapping("/graph/v3/query")
    fun query(
        @RequestBody actionBaseQuery: ActionbaseQuery,
    ): Mono<ResponseEntity<NamedQueryResult>> =
        v3QueryService
            .query(actionBaseQuery)
            .map {
                val items = it.map { entry -> entry.value.toNamedJsonFormat(entry.key) }
                NamedQueryResult(items)
            }.mapToResponseEntity()
}
