package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_REFRESH_TABLE

object AggregationSystemTables {
    private val SYSTEM_TABLES: Map<Pair<String, String>, AggregationType> =
        mapOf(
            (TOPK_DATABASE to TOPK_REFRESH_TABLE) to AggregationType.TOPK,
        )

    fun typeOf(
        database: String,
        table: String,
    ): AggregationType? = SYSTEM_TABLES[database to table]

    fun contains(
        database: String,
        table: String,
    ): Boolean = (database to table) in SYSTEM_TABLES
}
