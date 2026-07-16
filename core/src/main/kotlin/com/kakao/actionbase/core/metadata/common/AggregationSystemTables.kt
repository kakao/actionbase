package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.metadata.common.AggregationConstants.REFRESH_TABLE_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.REFRESH_TABLE_NAME

object AggregationSystemTables {
    private val SYSTEM_TABLES: Map<Pair<String, String>, AggregationType> =
        mapOf(
            (REFRESH_TABLE_DATABASE to REFRESH_TABLE_NAME) to AggregationType.TOPK,
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
