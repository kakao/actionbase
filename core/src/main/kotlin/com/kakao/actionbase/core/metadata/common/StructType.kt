package com.kakao.actionbase.core.metadata.common

data class StructType(
    val fields: List<StructField>,
) {
    private val fieldNames: List<String> by lazy { fields.map { it.name } }

    private val nameToField: Map<String, StructField> by lazy { fields.associateBy { it.name } }

    fun hasField(name: String): Boolean = name in nameToField

    fun getField(name: String): StructField = nameToField[name] ?: throw NoSuchElementException("Field '$name' not found. Available: ${fieldNames.joinToString()}")
}
