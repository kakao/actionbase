package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepResult
import com.kakao.actionbase.core.edge.payload.SweepItem
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

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMap { item -> handler(type).aggregate(item) }
            .collectList()

    fun sweep(items: List<SweepItem>): Mono<List<AggregationSweepResult>> =
        Flux
            .fromIterable(items)
            .flatMap { item -> handler(item.type).sweep(item.item) }
            .collectList()

    private fun handler(type: AggregationType): AggregationHandler = handlersByType[type] ?: error("No aggregation handler for type $type")
}
