package com.kakao.actionbase.engine.context

import com.fasterxml.jackson.annotation.JsonIgnore

data class RequestContext(
    val requestId: String,
    val actor: String,
    @field:JsonIgnore
    val includeContext: Boolean = false,
) {
    companion object {
        val DEFAULT = RequestContext("-", "-")
    }
}
