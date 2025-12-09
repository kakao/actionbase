package com.kakao.actionbase.core.types

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

enum class PrimitiveType {
    BOOLEAN {
        override fun cast(source: Any): Any =
            when (source) {
                is Boolean -> source
                else -> source.toString().toBoolean()
            }
    },
    BYTE {
        override fun cast(source: Any): Any =
            when (source) {
                is Byte -> source
                is Number -> source.toByte()
                else -> source.toString().toByte()
            }
    },
    SHORT {
        override fun cast(source: Any): Any =
            when (source) {
                is Short -> source
                is Number -> source.toShort()
                is String -> source.toShort()
                else -> source.toString().toShort()
            }
    },
    INT {
        override fun cast(source: Any): Any =
            when (source) {
                is Int -> source
                is Number -> source.toInt()
                is String -> source.toInt()
                else -> source.toString().toInt()
            }
    },
    LONG {
        override fun cast(source: Any): Any =
            when (source) {
                is Long -> source
                is Number -> source.toLong()
                is String -> source.toLong()
                else -> source.toString().toLong()
            }
    },
    FLOAT {
        override fun cast(source: Any): Any =
            when (source) {
                is Float -> source
                is Number -> source.toFloat()
                is String -> source.toFloat()
                else -> source.toString().toFloat()
            }
    },
    DOUBLE {
        override fun cast(source: Any): Any =
            when (source) {
                is Double -> source
                is Number -> source.toDouble()
                is String -> source.toDouble()
                else -> source.toString().toDouble()
            }
    },
    STRING {
        override fun cast(source: Any): Any =
            when (source) {
                is String -> source
                else -> source.toString()
            }
    },
    OBJECT {
        override fun cast(source: Any): Any =
            when (source) {
                is JsonNode -> source
                else -> mapper.valueToTree(source)
            }
    },
    ;

    @JsonValue
    fun toValue(): String = name.lowercase()

    abstract fun cast(source: Any): Any

    companion object {
        private val mapper = jacksonObjectMapper()
    }
}
