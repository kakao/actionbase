package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.AggregationMetadata
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

class AggregationService(
    private val engine: AggregationEngine,
) {
    fun getAggregations(): List<AggregationMetadata> = engine.getAllTables().map { it.toMetadata() }

    private fun V3TableDescriptor.toMetadata(): AggregationMetadata {
        val aggregations = schema.groupsOrNull().orEmpty().mapNotNull { it.aggregations }

        return AggregationMetadata(
            database = database,
            table = table,
            aggregations = aggregations,
        )
    }

    private fun ModelSchema.groupsOrNull(): List<Group>? =
        when (this) {
            is ModelSchema.Edge -> groups
            is ModelSchema.MultiEdge -> groups
            else -> null
        }
}
