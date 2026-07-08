package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.AggregationMetadata
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.QualifiedGroups

class AggregationService(
    private val engine: AggregationEngine,
) {
    fun getAggregations(): List<AggregationMetadata> = engine.getAllQualifiedGroups().map { it.toMetadata() }

    private fun QualifiedGroups.toMetadata(): AggregationMetadata =
        AggregationMetadata(
            database = database,
            table = table,
            aggregations = groups.map { it.aggregations },
        )
}
