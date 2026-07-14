package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.XXHash32Wrapper

import java.time.Clock

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
            clock: Clock = Clock.systemUTC(),
        ): Any = bucket?.handleQueryValue(value, ceil, clock)?.toString() ?: value
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
    val ranges: String = "",
    val expireAfterMillis: Long = -1,
    val table: TopkTable,
) {
    @get:JsonIgnore
    val scoreFqn: Pair<String, String> by lazy { parseFqn(table.score) }

    @get:JsonIgnore
    val expireFqn: Pair<String, String> by lazy { parseFqn(table.expire) }

    private companion object {
        private fun parseFqn(fqn: String): Pair<String, String> {
            val dot = fqn.indexOf('.')
            require(dot > 0 && dot < fqn.lastIndex) {
                "table must be a fully-qualified `database.table`, got: $fqn"
            }
            return fqn.substring(0, dot) to fqn.substring(dot + 1)
        }
    }
}

data class TopkTable(
    val score: String,
    val expire: String,
)

enum class AggregationType {
    TOPK,
}
