package com.kakao.actionbase.core.metadata.common

data class StructType(
    val fields: List<StructField>,
) {
    private val fieldNameSet: Set<String> by lazy { fields.mapTo(mutableSetOf()) { it.name } }

    fun hasField(name: String): Boolean = name in fieldNameSet
}
