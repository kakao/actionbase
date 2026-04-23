package com.kakao.actionbase.engine.context

import com.kakao.actionbase.engine.storage.StorageOpCollector

import com.fasterxml.jackson.annotation.JsonIgnore

data class RequestContext(
    val requestId: String,
    val actor: String,
    @JsonIgnore
    val includeContext: Boolean = false,
) {
    fun newCollector(): StorageOpCollector? = if (includeContext) StorageOpCollector() else null

    companion object {
        val DEFAULT = RequestContext("-", "-")
    }
}
