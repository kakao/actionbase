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
    val aggregations: Aggregations = Aggregations(),
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
)

data class Topk(
    val topk: String,
    val scope: TopkScope = TopkScope.LOCAL,
    val ranges: String = "",
    val refreshAfterMillis: Long = -1,
    val table: TopkTable,
)

data class TopkTable(
    val score: String,
    val refresh: String,
)

enum class TopkScope {
    LOCAL,
    GLOBAL,
}
