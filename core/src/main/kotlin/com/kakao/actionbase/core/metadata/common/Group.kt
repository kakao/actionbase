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

data class Topk(
    val topk: String,
    val scope: TopkScope = TopkScope.LOCAL,
    val rankTarget: RankTarget = RankTarget.TARGET,
    val ranges: String = "",
    val refreshAfterMillis: Long = -1,
    val table: TopkTable,
)

data class TopkTable(
    val score: String,
)

enum class TopkScope {
    LOCAL,
    GLOBAL,
}

// Which raw edge endpoint (source or target, independent of the group's declared direction) is
// the ranked value. The other endpoint is the entity a LOCAL topk is scoped to; it's meaningless
// for GLOBAL, where every event ranks into the same fixed entity regardless of rankTarget.
enum class RankTarget {
    SOURCE,
    TARGET,
}

enum class AggregationType {
    TOPK,
}
