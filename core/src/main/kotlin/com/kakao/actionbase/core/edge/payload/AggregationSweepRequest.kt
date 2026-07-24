package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.AggregationType

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class AggregationSweepRequest(
    val items: List<SweepItem>,
)

data class SweepItem(
    val type: AggregationType,

    @field:JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "type",
    )
    @field:JsonSubTypes(
        JsonSubTypes.Type(value = TopkSweepItem::class, name = "TOPK"),
    )
    val item: SweepItemPayload,
)

sealed interface SweepItemPayload

data class TopkSweepItem(
    val database: String,
    val table: String,
    val topk: String,
    val source: String,
    val target: String,
    val direction: String,
    val ranges: String = "",
    val entity: String,
    val topkDimensionValue: String,
    val dimensionValues: String = "",
    val properties: Map<String, String> = emptyMap(),
    val refreshAt: Long = -1,
) : SweepItemPayload
