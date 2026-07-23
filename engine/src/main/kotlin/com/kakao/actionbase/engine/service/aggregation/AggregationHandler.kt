package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepTarget
import com.kakao.actionbase.core.metadata.common.AggregationType

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * One aggregation kind's write path. [AggregationService] dispatches to the handler
 * whose [type] matches the request, so a new [AggregationType] is added by providing a
 * new handler bean — no changes to the dispatcher.
 */
interface AggregationHandler {
    val type: AggregationType

    /** Aggregates a single edge event and writes its result rows. */
    fun aggregate(item: AggregationItemPayload): Flux<AggregationResult>

    /** Recomputes a single refreshed target and re-writes its result row. */
    fun sweep(target: AggregationSweepTarget): Mono<AggregationSweepResult>
}
