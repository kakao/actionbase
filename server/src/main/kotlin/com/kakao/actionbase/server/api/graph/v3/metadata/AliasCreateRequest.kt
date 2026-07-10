package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AliasCreateRequest(
    @field:NotBlank(message = "alias is required")
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val alias: String,
    @field:NotBlank(message = "table is required")
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val table: String,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String,
)
