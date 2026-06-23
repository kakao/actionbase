package com.kakao.actionbase.core.vertex.payload

data class VertexPayload(
    val version: Long,
    val id: Any,
    val properties: Map<String, Any?>,
    val context: Map<String, Any?>,
)
