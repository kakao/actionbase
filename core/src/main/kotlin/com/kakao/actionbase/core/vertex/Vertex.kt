package com.kakao.actionbase.core.vertex

data class Vertex(
    val version: Long,
    val id: Any,
    val properties: Map<String, Any?> = emptyMap(),
)
