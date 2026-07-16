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
    fun isEmpty(): Boolean = supportedTypes.isEmpty()

    @get:JsonIgnore
    val supportedTypes: Set<AggregationType> by lazy {
        setOfNotNull(AggregationType.TOPK.takeIf { topk.isNotEmpty() })
    }

    companion object {
        val EMPTY = Aggregations()
    }
}

// `entity` names the field whose value the ranking is scoped to (the score key's entity block):
// "_source"/"_target" for the raw edge endpoints (independent of the group's declared direction),
// a property name, or the __global__ sentinel for a single ranking across all events.
// `rankedField` names the field whose value is being ranked (the score row's target).
data class Topk(
    val topk: String,
    val entity: String = AggregationConstants.SOURCE_FIELD,
    val rankedField: String = AggregationConstants.TARGET_FIELD,
    val ranges: String = "",
    val refreshAfterMillis: Long = -1,
    val table: TopkTable,
)

data class TopkTable(
    val score: String,
)

enum class AggregationType {
    TOPK,
}
