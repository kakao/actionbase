package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.MutationMode

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

/**
 * Mirrors the updatable set of the v2 `LabelUpdateRequest`. A table's `type` and `direction` are
 * fixed at creation, so neither appears here; omitting a field leaves it untouched.
 */
data class TableUpdateRequest(
    val active: Boolean? = null,
    @field:Valid
    val schema: TableSchema? = null,
    val indexes: List<Index>? = null,
    val groups: List<Group>? = null,
    val caches: List<Cache>? = null,
    val mode: MutationMode? = null,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String? = null,
)
