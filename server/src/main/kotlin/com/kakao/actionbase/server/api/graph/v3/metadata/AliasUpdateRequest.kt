package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AliasUpdateRequest(
    val active: Boolean? = null,
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val table: String? = null,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String? = null,
)
