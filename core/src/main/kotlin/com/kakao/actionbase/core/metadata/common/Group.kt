package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.XXHash32Wrapper

import com.fasterxml.jackson.annotation.JsonIgnore

data class Group(
    val group: String,
    val type: GroupType,
    val fields: List<Field>,
    val valueField: String = "-",
    val comment: String = Constants.DEFAULT_COMMENT,
    val directionType: DirectionType = DirectionType.BOTH,
    val ttl: Long = Constants.Group.DEFAULT_TTL,
    val aggregations: Aggregations = Aggregations.EMPTY,
) {
    @JsonIgnore
    val code = XXHash32Wrapper.default.stringHash(group)

    data class Field(
        val name: String,
        val bucket: Bucket? = null,
    ) {
        fun bucketOrGet(
            value: Any,
            ceil: Boolean,
        ): Any = bucket?.handleQueryValue(value, ceil)?.toString() ?: value
    }
}

data class Aggregations(
    val topk: List<Topk> = emptyList(),
) {
    @JsonIgnore
    fun isEmpty(): Boolean = AggregationType.entries.none { it.has(this) }

    @JsonIgnore
    fun supports(type: AggregationType): Boolean = type.has(this)

    companion object {
        val EMPTY = Aggregations()
    }
}

data class Topk(
    val topk: String,
    val entity: String,
    val ranges: String = "",
    val dimension: String,
    val refreshAfterMillis: Long = -1,
    val rank: String,
)

enum class AggregationType {
    TOPK {
        override fun has(aggregations: Aggregations): Boolean = aggregations.topk.isNotEmpty()
    }, ;

    abstract fun has(aggregations: Aggregations): Boolean
}
