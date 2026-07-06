package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.XXHash32Wrapper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

data class Group(
    val group: String,
    val type: GroupType,
    val fields: List<Field>,
    val valueField: String = "-",
    val comment: String = Constants.DEFAULT_COMMENT,
    val directionType: DirectionType = DirectionType.BOTH,
    val ttl: Long = Constants.Group.DEFAULT_TTL,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val aggregations: Aggregations? = null,
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
    val ranges: String? = null,
    val expire: Boolean = false,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val expireAfterMillis: Long? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val table: TopkTable? = null,
) {
    init {
        if (expire) {
            require(expireAfterMillis != null && expireAfterMillis > 0) {
                "topk `$topk`: expireAfterMillis must be set to a positive value when expire=true"
            }
        } else {
            require(expireAfterMillis == null) {
                "topk `$topk`: expireAfterMillis must be null when expire=false"
            }
        }
    }
}

data class TopkTable(
    val score: String,
    val expire: String,
)
