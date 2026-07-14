package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.AggregationEngine

class AggregationService(
    private val engine: AggregationEngine,
) {
    fun getAggregations(type: AggregationType? = null): List<QualifiedAggregations> = engine.getListWithAggregations(type)
}
