package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.service.aggregation.AggregationHandler

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val engine: AggregationEngine,
    handlers: List<AggregationHandler>,
) {
    private val handlersByType: Map<AggregationType, AggregationHandler> = handlers.associateBy { it.type }

    fun getAggregations(type: AggregationType? = null): List<QualifiedAggregations> = engine.getListWithAggregations(type)

    fun aggregate(items: List<AggregationItemPayload>): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMap { item -> Flux.merge(handlersByType.values.map { it.aggregate(item) }) }
            .collectList()
}
