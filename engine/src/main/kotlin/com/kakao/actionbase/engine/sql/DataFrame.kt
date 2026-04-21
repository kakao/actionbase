package com.kakao.actionbase.engine.sql

import com.kakao.actionbase.core.metadata.common.StructType

data class DataFrame(
    val rows: List<Row>,
    val schema: StructType,
    val count: Int = rows.size,
    val total: Long,
    val offset: String? = null,
    val hasNext: Boolean = false,
) {
    companion object {
        const val COUNT_FIELD = "COUNT(1)"

        val empty: DataFrame =
            DataFrame(
                rows = emptyList(),
                schema = StructType(emptyList()),
                total = 0L,
            )
    }
}
