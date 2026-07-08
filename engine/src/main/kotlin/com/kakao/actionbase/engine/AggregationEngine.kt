package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding

interface AggregationEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun getAllQualifiedGroups(): List<QualifiedGroups>
}
