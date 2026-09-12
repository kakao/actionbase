package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.MutationMode

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class TableCreateRequest(
    @field:NotBlank(message = "table is required")
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val table: String,
    @field:NotNull(message = "type is required")
    val type: TableType,
    @field:NotNull(message = "schema is required")
    @field:Valid
    val schema: TableSchema,
    @field:NotNull(message = "direction is required")
    val direction: DirectionType,
    @field:NotBlank(message = "storage is required")
    @field:Pattern(regexp = Constants.Name.STORAGE_URI_PATTERN, message = Constants.Name.STORAGE_URI_MESSAGE)
    val storage: String,
    val indexes: List<Index> = emptyList(),
    val groups: List<Group> = emptyList(),
    val caches: List<Cache> = emptyList(),
    val mode: MutationMode = MutationMode.SYNC,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String,
) {
    init {
        // The v2 metastore does not know about immutable edges, so the surface holds these invariants.
        if (type == TableType.IMMUTABLE_EDGE) {
            require(indexes.size <= 1) {
                "immutable edge allows at most one index (scan-and-delete evicts by scanning one), got ${indexes.map { it.index }}"
            }
            require(direction != DirectionType.BOTH) {
                "immutable edge must be single-direction OUT or IN (scan-and-delete evicts one direction), got BOTH"
            }
        }
    }
}
