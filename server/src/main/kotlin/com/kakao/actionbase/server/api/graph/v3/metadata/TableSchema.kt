package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.VertexType

import com.fasterxml.jackson.annotation.JsonProperty

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * The v3 name for a v2 edge schema: `src`/`tgt`/`fields` renamed to `source`/`target`/`properties`,
 * and each `desc` renamed to `comment`. The structure and the type values are v2's, unchanged.
 *
 * A vertex table carries its id in [source] and a placeholder in [target]; a multi-edge table
 * carries its id as the `_id` property. Both follow v2 exactly.
 */
data class TableSchema(
    @field:NotNull(message = "schema.source is required")
    @field:Valid
    val source: Key,
    @field:NotNull(message = "schema.target is required")
    @field:Valid
    val target: Key,
    @field:Valid
    val properties: List<Property> = emptyList(),
) {
    data class Key(
        @field:NotNull(message = "type is required")
        val type: VertexType,
        val comment: String = "",
    )

    data class Property(
        @field:NotBlank(message = "name is required")
        val name: String,
        @field:NotNull(message = "type is required")
        val type: DataType,
        @JsonProperty("nullable")
        val nullable: Boolean = false,
        val comment: String = "",
    )
}
