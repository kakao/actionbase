package com.kakao.actionbase.engine.sql

import com.kakao.actionbase.core.metadata.common.StructType

data class Row(
    val data: Map<String, Any?>,
    val schema: StructType,
) {
    val size: Int get() = data.size

    operator fun get(fieldName: String): Any? {
        if (!schema.hasField(fieldName)) {
            throw IllegalArgumentException("Field '$fieldName' does not exist in the schema.")
        }
        return data[fieldName]
    }
}
