package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

interface AggregationEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun getAllTables(): List<V3TableDescriptor>
}
