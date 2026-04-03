package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.sql.DataFrame

import reactor.core.publisher.Mono

/**
 * Query engine abstraction used by the V3 Query path.
 * Decouples QueryService from Graph by exposing only the operations it needs.
 */
interface QueryEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>>
}
