package com.kakao.actionbase.server.api.graph.v3.metadata

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AliasCreateRequest(
    @field:NotBlank(message = "alias is required")
    @field:Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,63}$",
        message = "alias must start with a letter, contain only alphanumeric/underscore/hyphen, max 64 chars",
    )
    val alias: String,
    @field:NotBlank(message = "table is required")
    @field:Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,63}$",
        message = "table must start with a letter, contain only alphanumeric/underscore/hyphen, max 64 chars",
    )
    val table: String,
    @field:Size(max = 1000, message = "comment must be at most 1000 characters")
    val comment: String,
)
