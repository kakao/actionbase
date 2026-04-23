package com.kakao.actionbase.v2.engine.edge

import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus

import com.fasterxml.jackson.annotation.JsonInclude

data class MutationResult(
    val result: List<MutationResultItem>,
)

data class MutationResultItem(
    val status: EdgeOperationStatus,
    val traceId: String,
    val edge: HashEdge?,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val context: Map<String, Any?>? = null,
)
