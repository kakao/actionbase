package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AliasUpdateRequest(
    val active: Boolean? = null,
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val table: String? = null,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String? = null,
)
