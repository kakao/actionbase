package com.kakao.actionbase.engine

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.binding.TableBinding

interface AggregationEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun getListWithAggregations(type: AggregationType? = null): List<QualifiedAggregations>
}
