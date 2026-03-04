package com.kakao.actionbase.core.metadata.payload

data class DatabaseUpdateRequest(
    val active: Boolean? = null,
    val comment: String? = null,
    val version: Long = System.currentTimeMillis(),
)
